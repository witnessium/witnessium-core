package org.witnessium.core
package node
package endpoint

import cats.effect.IO
import io.finch._
import io.finch.circe._
import io.finch.catsEffect._

import codec.circe._
import model.Transaction
import service.TransactionService

class TransactionEndpoint(transactionService: TransactionService[IO]) {

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  val Post: Endpoint[IO, UInt256Refine.UInt256Bytes] = post("transaction"::jsonBody[Transaction]) { (t: Transaction) =>
    transactionService.submit(t).map{
      case Left(msg) => BadRequest(new Exception(msg))
      case Right(bytes) => Ok(bytes)
    }
  }
}
