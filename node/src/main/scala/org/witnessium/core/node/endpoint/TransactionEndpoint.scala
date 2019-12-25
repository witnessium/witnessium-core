package org.witnessium.core
package node
package endpoint

import cats.effect.{Async, Timer}
import cats.implicits._
import io.circe.generic.auto._
import io.circe.refined._
import io.finch._
import io.finch.circe._

import codec.circe._
import crypto.KeyPair
import datatype.{MerkleTrieNode, UInt256Bytes}
import model.{Address, Transaction}
import model.api.{TransactionInfo, TransactionInfoBrief}
import repository.{BlockRepository, TransactionRepository}
import service.TransactionService
import store.HashStore

object TransactionEndpoint {

  def Index[F[_]: Async: BlockRepository: TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, List[TransactionInfoBrief]] = {

    import finch._

    get("transaction"
      :: param[Address]("address")
      :: paramOption[Int]("offset")
      :: paramOption[Int]("limit")
    ) { (address: Address, offsetOption: Option[Int], limitOption: Option[Int]) =>
      TransactionService.findByAddress[F](address, offsetOption getOrElse 0, limitOption getOrElse 10).value.map {
        case Right(txInfos) => Ok(txInfos)
        case Left(errorMsg) =>
          scribe.info(
            s"Index transaction with address: $address offset:$offsetOption limit:limitOption error response: $errorMsg"
          )
          InternalServerError(new Exception(errorMsg))
      }
    }
  }

  def Get[F[_]: Async: TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, Transaction.Verifiable] = {

    import finch._

    get("transaction" ::
      path[UInt256Bytes].withToString("{transactionHash}")
    ) { (transactionHash: UInt256Bytes) =>
      scribe.info(s"Receive get transaction request: $transactionHash")
      TransactionService.get[F](transactionHash).value.map {
        case Right(Some(tx)) => Ok(tx)
        case Right(None) => NotFound(new Exception(s"Not found: $transactionHash"))
        case Left(errorMsg) =>
          scribe.info(s"Get transaction $transactionHash error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }

  type MerkleHashStore[F[_]] = HashStore[F, MerkleTrieNode]

  def Post[F[_]: Async: Timer: BlockRepository: TransactionRepository: MerkleHashStore](localKeyPair: KeyPair)(implicit
    finch: EndpointModule[F]
  ): Endpoint[F, UInt256Bytes] = {

    import finch._

    post("transaction"::jsonBody[Transaction.Signed]) { (t: Transaction.Signed) =>
      scribe.info(s"Receive post transaction request: $t")
      TransactionService.submit[F](t, localKeyPair).value.map{
        case Right(txHash) => Ok(txHash)
        case Left(errorMsg) =>
          scribe.info(s"Post transaction $t error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }

  def GetInfo[F[_]: Async: BlockRepository: TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, TransactionInfo] = {

    import finch._

    get("txinfo" :: path[UInt256Bytes].withToString("{transactionHash}")) { (transactionHash: UInt256Bytes) =>
      scribe.info(s"Receive get transaction info request: $transactionHash")
      TransactionService.getInfo[F](transactionHash).value.map {
        case Right(Some(txInfo)) => Ok(txInfo)
        case Right(None) => NotFound(new Exception(s"Not found: $transactionHash"))
        case Left(errorMsg) =>
          scribe.info(s"Get transaction info $transactionHash error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }
}
