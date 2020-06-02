package org.witnessium.core
package node
package endpoint

import cats.effect.Async
import cats.implicits._
import io.finch._

import crypto.MerkleTrie.NodeStore
import model.Account
import model.api.{AccountInfo, AccountUtxoInfo}
import repository.{BlockRepository, TransactionRepository}
import service.AccountService

object AccountEndpoint {

  def GetUtxo[F[_]: Async: BlockRepository: NodeStore: TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, AccountUtxoInfo] = {
    import finch._

    get("account" :: "utxo" :: path[Account].withToString("{account}")) { (account: Account) =>
      AccountService.balanceWithUnusedTxhashes[F](account).value.map {
        case Right((balance, txHashes)) => Ok(AccountUtxoInfo(account, balance, txHashes))
        case Left(errorMsg) =>
          scribe.info(s"Get account UTXO of $account error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }

  def GetInfo[F[_]: Async: BlockRepository: NodeStore: TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, AccountInfo] = {
    import finch._

    get("account" :: path[Account].withToString("{account}")) { (account: Account) =>
      AccountService.getInfo[F](account).value.map {
        case Right(accountInfo) => Ok(accountInfo)
        case Left(errorMsg) =>
          scribe.info(s"Get account info of $account error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }
}
