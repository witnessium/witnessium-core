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
import datatype.{MerkleTrieNode, UInt256Bytes}
import model.{Address, Transaction}
import model.api.{TransactionInfo, TransactionInfoBrief}
import repository.{BlockRepository, TransactionRepository}
import service.TransactionService
import store.HashStore

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class TransactionEndpoint(
  localKeyPair: KeyPair
)(implicit
  timer: Timer[IO],
  blockRepository: BlockRepository[IO],
  transactionRepository: TransactionRepository[IO],
  hashStore: HashStore[IO, MerkleTrieNode],
) {

  val Index: Endpoint[IO, List[TransactionInfoBrief]] = get("transaction" ::
    param[Address]("address") ::
    paramOption[Int]("offset") ::
    paramOption[Int]("limit")
  ) { (address: Address, offsetOption: Option[Int], limitOption: Option[Int]) =>
    TransactionService.findByAddress[IO](address, offsetOption getOrElse 0, limitOption getOrElse 10).value.map {
      case Right(txInfos) => Ok(txInfos)
      case Left(errorMsg) =>
        scribe.info(
          s"Index transaction with address: $address offset:$offsetOption limit:limitOption error response: $errorMsg"
        )
        InternalServerError(new Exception(errorMsg))
    }
  }

  val Get: Endpoint[IO, TransactionInfo] = get("transaction" ::
    path[UInt256Bytes].withToString("{transactionHash}")
  ) { (transactionHash: UInt256Bytes) =>
    scribe.info(s"Receive get transaction request: $transactionHash")
    TransactionService.get[IO](transactionHash).value.map {
      case Right(Some(txInfo)) => Ok(txInfo)
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
