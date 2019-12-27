package org.witnessium.core
package node
package endpoint

import cats.Applicative
import cats.effect.{Concurrent, Timer}
import cats.implicits._
import com.twitter.finagle.http.exp.Multipart
import io.finch._

import datatype.UInt256Bytes
import model.NetworkId
import crypto.KeyPair
import repository.{BlockRepository, TransactionRepository}
import service.TicketService

object TicketEndpoint{

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
        case Right(ticketData) =>
          TicketService.submit[F](ticketData, networkId, localKeyPair).value.map {
            case Left(errorMsg) =>
              InternalServerError(new Exception(errorMsg))
            case Right(txHash) =>
              Ok(txHash)
          }
      }
    }
  }
}
