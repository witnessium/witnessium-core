package org.witnessium.core
package node
package endpoint

import cats.effect.{IO, Timer}
import io.circe.generic.auto._
import io.circe.refined._
import io.finch._
import io.finch.circe._
import io.finch.catsEffect._

import codec.circe._
import crypto.KeyPair
import datatype.UInt256Bytes
import model.Transaction
import repository.{BlockRepository, TransactionRepository}
import service.TransactionService

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class TransactionEndpoint(
  localKeyPair: KeyPair
)(implicit
  timer: Timer[IO],
  blockRepository: BlockRepository[IO],
  transactionRepository: TransactionRepository[IO],
) {

  val Get: Endpoint[IO, Transaction.Verifiable] = get("transaction" ::
    path[UInt256Bytes].withToString("{transactionHash}")
  ) { (transactionHash: UInt256Bytes) =>
    scribe.info(s"Receive get transaction request: $transactionHash")
    transactionRepository.get(transactionHash).value.map {
      case Right(Some(transactionVerifiable)) => Ok(transactionVerifiable)
      case Right(None) => NotFound(new Exception(s"Not found: $transactionHash"))
      case Left(errorMsg) =>
        scribe.info(s"Get transaction $transactionHash error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }

  val Post: Endpoint[IO, UInt256Bytes] =
    post("transaction"::jsonBody[Transaction.Signed]) { (t: Transaction.Signed) =>
      scribe.info(s"Receive post transaction request: $t")
      TransactionService.submit[IO](t, localKeyPair).value.map{
        case Right(txHash) => Ok(txHash)
        case Left(errorMsg) =>
          scribe.info(s"Post transaction $t error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
}
