package org.witnessium.core
package node
package repository

import cats.Monad
import cats.data.EitherT
import cats.implicits._

import crypto.Hash.ops._
import datatype.{UInt256Bytes, UInt256Refine}
import model.{Account, Transaction}
import store.{HashStore, StoreIndex}

trait TransactionRepository[F[_]] {
  def get(transactionHash: UInt256Bytes): EitherT[F, String, Option[Transaction.Verifiable]]
  def put(transaction: Transaction.Verifiable): EitherT[F, String, Unit]

  def buildIndex(
    accountOption: Option[Account], transaction: Transaction.Verifiable
  ): F[Unit]
  def listByAccount(account: Account, offset: Int, limit: Int): EitherT[F, String, List[UInt256Bytes]]
}

object TransactionRepository {

  implicit def fromStores[F[_]: Monad](implicit
    transctionHashStore: HashStore[F, Transaction.Verifiable],
    accountTransactionIndex: StoreIndex[F, (Account, UInt256Bytes), Unit],
  ): TransactionRepository[F] = new TransactionRepository[F] {

    def get(transactionHash: UInt256Bytes): EitherT[F,String,Option[Transaction.Verifiable]] =
      transctionHashStore.get(transactionHash)

    def put(transaction: Transaction.Verifiable): EitherT[F,String,Unit] = for {
      _ <- transctionHashStore.put(transaction)
    } yield ()

    def buildIndex(
      accountOption: Option[Account], transaction: Transaction.Verifiable
    ): F[Unit] = {
      val txHash = transaction.toHash
      val outputAccoounts: Set[Account] = transaction.value.outputs.map(_._1)
      val accountList: List[Account] = outputAccoounts.toList ::: accountOption.toList

      accountList.traverse{ account =>
        accountTransactionIndex.put((account, txHash), ())
      }.map(_ => ())
    }

    def listByAccount(account: Account, offset: Int, limit: Int): EitherT[F, String, List[UInt256Bytes]] =
      accountTransactionIndex.from((account, UInt256Refine.EmptyBytes), offset, limit).map{ list =>
        for {
          ((account1, txHash), ()) <- list if account1 === account
        } yield txHash
      }
  }
}
