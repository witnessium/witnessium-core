package org.witnessium.core
package node
package endpoint

import cats.effect.Async
import cats.implicits._
import io.finch._
import datatype.UInt256Bytes
import repository.TransactionRepository
import service.TransactionService
import view.SmsNoti

object NotificationEndpoint {

  def Get[F[_]: Async: TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, Html] = {

    import finch._

    get("notification" :: path[UInt256Bytes].withToString("{txHash}")){ (txHash: UInt256Bytes) =>

      TransactionService.get[F](txHash).value.map{
        case Right(Some(tx)) if tx.value.ticketData.flatMap(_.driverName).nonEmpty =>
          Ok(SmsNoti.render(
            tx.value.ticketData.flatMap(_.driverName).getOrElse(""),
            tx.value.ticketData.flatMap(_.footage).fold(""){ footage =>
              s"${ApiPath.ticket.file.toUrl}/${txHash.toHex}-${footage.filename}"
            },
            s"https://explorer.traffic.demo.witnessium.org/tx-hash/${txHash.toBytes.toHex}"
          ))
        case Right(_) => NotFound(new Exception(s"Not found: $txHash"))
        case Left(errorMsg) =>
          scribe.info(s"Get transaction $txHash error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }
}
