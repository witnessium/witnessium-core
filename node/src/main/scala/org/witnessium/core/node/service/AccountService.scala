package org.witnessium.core
package node
package service

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import crypto.MerkleTrie.{MerkleTrieState, NodeStore}
import datatype.UInt256Bytes
import model.{Account, Transaction}
import model.api.{AccountInfo, TransactionInfoBrief}
import repository.{BlockRepository, StateRepository, TransactionRepository}
import StateRepository._

object AccountService{

  def unusedTxHashes[F[_]: Sync: BlockRepository: NodeStore](
    account: Account
  ): EitherT[F, String, List[UInt256Bytes]] = for {
    bestHeaderOption <- implicitly[BlockRepository[F]].bestHeader
    bestHeader <- EitherT.fromOption[F](bestHeaderOption, s"No best header in finding unused tx hashes: $account")
    all <- MerkleTrieState.fromRoot(bestHeader.stateRoot).getAll
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"=== all state contents ===")))
    _ <- EitherT.right(Sync[F].pure(all.foreach{ (content) => scribe.info(s"$content") }))
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"==========================")))
    txHashes <- MerkleTrieState.fromRoot(bestHeader.stateRoot).get(account)
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"$account utxo: $txHashes")))
  } yield txHashes

  def unusedTxs[F[_]: Sync: BlockRepository: NodeStore: TransactionRepository](
    account: Account
  ): EitherT[F, String, (List[Transaction.Verifiable], List[UInt256Bytes])] = for {
    txHashes <- unusedTxHashes[F](account)
    txs <- txHashes.traverse{ txHash =>
      for {
        txOption <- implicitly[TransactionRepository[F]].get(txHash)
        tx <- EitherT.fromOption[F](txOption, s"Transaction $txHash is not exist")
      } yield tx
    }
  } yield (txs, txHashes)

  def balanceFromUnusedTxs(account: Account)(txs: List[Transaction.Verifiable]): BigInt = (for {
    tx <- txs
    (account1, amount) <- tx.value.outputs if account1 === account
  } yield amount.value).sum

  def balance[F[_]: Sync: BlockRepository: NodeStore: TransactionRepository](
    account: Account
  ): EitherT[F, String, BigInt] = unusedTxs[F](account) map(_._1) map balanceFromUnusedTxs(account)

  def balanceWithUnusedTxhashes[F[_]: Sync: BlockRepository: NodeStore: TransactionRepository](
    account: Account
  ): EitherT[F, String, (BigInt, List[UInt256Bytes])] = for {
    (txs, txHashes) <- unusedTxs[F](account)
  } yield (balanceFromUnusedTxs(account)(txs), txHashes)

  def txInfoToTras(currentAccount: Account)(txInfo: TransactionInfoBrief): AccountInfo.Transaction = {

    def addMyAccount(items: List[AccountInfo.Item]): List[AccountInfo.Item] = items match {
      case Nil => Nil
      case x :: xs => x.copy(myAccount = Some(currentAccount)) :: xs
    }

    if (txInfo.inputAccount === Option(currentAccount)) {
      //sender
      val items = txInfo.outputs.map{ case (toAccount, amount) =>
        AccountInfo.Item(
          myAccount = None,
          receiveAccount = None,
          sendAccount = Some(toAccount),
          value = amount,
        )
      }

      AccountInfo.Transaction(
        `type` = "sender",
        tranHash = txInfo.txHash,
        timestamp = txInfo.confirmedAt,
        items = addMyAccount(items),
      )
    } else {
      //receiver
      val items = txInfo.outputs.map{ case (toAccount, amount) =>
        AccountInfo.Item(
          myAccount = None,
          receiveAccount = Some(toAccount),
          sendAccount = None,
          value = amount,
        )
      }

      AccountInfo.Transaction(
        `type` = "receiver",
        tranHash = txInfo.txHash,
        timestamp = txInfo.confirmedAt,
        items = addMyAccount(items),
      )
    }
  }

  def getInfo[F[_]: Sync: BlockRepository: NodeStore: TransactionRepository](
    account: Account
  ): EitherT[F, String, AccountInfo] = for {
    balanceValue <- balance[F](account)
    txinfos <- TransactionService.findByAccount[F](account, 0, Int.MaxValue)
  } yield AccountInfo(
    accountInfo = AccountInfo.Balance(balance = balanceValue),
    trans = txinfos map txInfoToTras(account),
  )
}
