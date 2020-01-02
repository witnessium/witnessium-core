package org.witnessium.core
package node
package endpoint

import cats.Applicative
import cats.effect.{Async, Concurrent, Timer}
import cats.implicits._
import com.twitter.finagle.http.exp.Multipart
import com.twitter.io.Buf
import io.finch._

import datatype.UInt256Bytes
import model.NetworkId
import model.api.LicenseInfo
import crypto.KeyPair
import repository.{BlockRepository, TransactionRepository}
import service.TicketService

object TicketEndpoint{

  def Index[F[_]: Async: BlockRepository: TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, LicenseInfo] = {

    import finch._

    get("ticket"
      :: param[String]("license")
      :: paramOption[Int]("offset")
      :: paramOption[Int]("limit")
    ) { (license: String, offsetOption: Option[Int], limitOption: Option[Int]) =>
      TicketService.findByLicense[F](license, offsetOption getOrElse 0, limitOption getOrElse 10).value.map {
        case Right(licneseInfo) => Ok(licneseInfo)
        case Left(errorMsg) =>
          scribe.info(
            s"Index ticket with license: $license offset:$offsetOption limit:limitOption error response: $errorMsg"
          )
          InternalServerError(new Exception(errorMsg))
      }
    }
  }

  def GetAttachment[F[_]: Async: TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, Buf] = {
    import finch._

    get(ApiPath.ticket.file.toEndpoint ::
      path[String].withToString("{txHash-filename}")
    ){ (pathTail: String) =>

      scribe.info(s"Get attachment request: $pathTail")

      val (front, back) = pathTail splitAt pathTail.indexOf('-')

      val response: F[Output[Buf]] = (for {
        backTail <- if (back.isEmpty) None else Some(back.tail)
        txHash <- DecodePath[UInt256Bytes].apply(front)
        filename <- DecodePath[String].apply(backTail)
      } yield (txHash, filename)) match {
        case Some((txHash,  filename)) => TicketService.getAttachment[F](txHash, filename).value.map {
          case Right(Some(photo)) =>
            scribe.info(s"Photo response: $photo")
            Ok(Buf.ByteArray.Owned(photo.content.toArray))
          case Right(None) =>
            scribe.info(s"Photo not found")
            NotFound(new Exception(s"Not found: $pathTail"))
          case Left(errorMsg) =>
            scribe.info(s"Get ticket file $pathTail error response: $errorMsg")
            InternalServerError(new Exception(errorMsg))
        }
        case None =>
          Applicative[F].pure(BadRequest(new Exception(s"Bad Request: $pathTail")))
      }

      response
    }
  }

  def Post[F[_]: Concurrent: BlockRepository: TransactionRepository: Timer](
    networkId: NetworkId,
    localKeyPair: KeyPair,
  )(implicit finch: EndpointModule[F]): Endpoint[F, UInt256Bytes] = {

    import finch._

    post("ticket"
      :: multipartFileUploadOption("photo")
      :: multipartAttributeOption[String]("owner")
      :: multipartAttributeOption[String]("license")
      :: multipartAttributeOption[String]("car")
      :: multipartAttributeOption[String]("phone")
      :: multipartAttributeOption[String]("violation")
      :: multipartAttributeOption[String]("location")
      :: multipartAttributeOption[String]("datetime")
      :: multipartAttributeOption[String]("amount")
      :: multipartAttributeOption[String]("ticketTxHash")
      :: multipartAttributeOption[String]("payedAt")
      :: multipartAttributeOption[String]("paymentDescription")
    ) { (
      fileUpload        : Option[Multipart.FileUpload],
      owner             : Option[String],
      license           : Option[String],
      car               : Option[String],
      phone             : Option[String],
      violation         : Option[String],
      location          : Option[String],
      datetime          : Option[String],
      amount            : Option[String],
      ticketTxHash      : Option[String],
      payedAt           : Option[String],
      paymentDescription: Option[String],
    ) =>

      TicketService.ticketData[F](
        fileUpload = fileUpload,
        owner = owner,
        license = license,
        car = car,
        phone = phone,
        violation = violation,
        location = location,
        datetime = datetime,
        amount = amount,
        ticketTxHash = ticketTxHash,
        payedAt = payedAt,
        paymentDescription = paymentDescription,
      ).value.flatMap[Output[UInt256Bytes]] {
        case Left(errorMsg) =>
          Applicative[F].pure(BadRequest(new Exception(errorMsg)))
        case Right((ticketData, attachment)) =>
          TicketService.submit[F](ticketData, attachment, networkId, localKeyPair).value.map {
            case Left(errorMsg) =>
              InternalServerError(new Exception(errorMsg))
            case Right(txHash) =>
              Ok(txHash)
          }
      }
    }
  }
}
