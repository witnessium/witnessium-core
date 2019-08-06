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
import datatype.UInt256Bytes
import model.Transaction
import service.{BlockExplorerService, TransactionService}

class TransactionEndpoint(transactionService: TransactionService[IO], blockExplorerService: BlockExplorerService[IO]) {

  val Get: Endpoint[IO, Transaction.Verifiable] = get("trasaction" ::
    path[UInt256Bytes].withToString("transactionHash")
  ) { (transactionHash: UInt256Bytes) =>
    blockExplorerService.transaction(transactionHash).map {
      case Right(Some(transactionVerifiable)) => Ok(transactionVerifiable)
      case Right(None) => NotFound(new Exception(s"Not found: $transactionHash"))
      case Left(errorMsg) =>
        scribe.info(s"Get transaction $transactionHash error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }

  val Post: Endpoint[IO, UInt256Bytes] = post("transaction"::jsonBody[Transaction.Signed]) { (t: Transaction.Signed) =>
    transactionService.submit(t).map{
      case Left(msg) => BadRequest(new Exception(msg))
      case Right(bytes) => Ok(bytes)
    }
  }
}
