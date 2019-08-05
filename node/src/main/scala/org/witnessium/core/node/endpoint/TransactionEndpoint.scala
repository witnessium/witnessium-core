package org.witnessium.core
package node
package endpoint

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.refined._
import io.finch._
import io.finch.circe._
import io.finch.catsEffect._

import codec.circe._
import datatype.UInt256Refine
import model.Transaction
import service.TransactionService

class TransactionEndpoint(transactionService: TransactionService[IO]) {

  val Post: Endpoint[IO, UInt256Refine.UInt256Bytes] = post("transaction"::jsonBody[Transaction]) { (t: Transaction) =>
    transactionService.submit(t).map{
      case Left(msg) => BadRequest(new Exception(msg))
      case Right(bytes) => Ok(bytes)
    }
  }
}
