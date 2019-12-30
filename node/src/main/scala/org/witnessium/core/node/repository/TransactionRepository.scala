package org.witnessium.core
package node
package repository

import cats.Monad
import cats.data.EitherT
import cats.implicits._

import crypto._
import crypto.Hash.ops._
import datatype.{UInt256Bytes, UInt256Refine}
import model.{Address, Genesis, Signed, Transaction}
import store.{HashStore, StoreIndex}

trait TransactionRepository[F[_]] {
  def get(transactionHash: UInt256Bytes): EitherT[F, String, Option[Transaction.Verifiable]]
  def put(transaction: Transaction.Verifiable): EitherT[F, String, Unit]

  def listByAddress(address: Address, offset: Int, limit: Int): EitherT[F, String, List[UInt256Bytes]]
  def listByLicense(license: String, offset: Int, limit: Int): EitherT[F, String, List[UInt256Bytes]]
}

object TransactionRepository {

  implicit def fromStores[F[_]: Monad](implicit
    transctionHashStore: HashStore[F, Transaction.Verifiable],
    addressTransactionIndex: StoreIndex[F, (Address, UInt256Bytes), Unit],
    licenseTransactionIndex: StoreIndex[F, (String, UInt256Bytes), Unit],
  ): TransactionRepository[F] = new TransactionRepository[F] {

    def get(transactionHash: UInt256Bytes): EitherT[F,String,Option[Transaction.Verifiable]] =
      transctionHashStore.get(transactionHash)

    def put(transaction: Transaction.Verifiable): EitherT[F,String,Unit] = for {
      _ <- transctionHashStore.put(transaction)
      txHash = transaction.toHash
      incomingAddressOption <- (transaction match {
        case Genesis(_) => EitherT.pure[F, String](None)
        case Signed(sig, value) => for {
          pubKey <- EitherT.fromEither[F](sig.signedMessageHashToKey(txHash))
          incomingAddress = Address.fromPublicKeyHash(pubKey.toHash)
          _ <- EitherT.right[String](value.inputs.toList.traverse{ txHash =>
            addressTransactionIndex.put((incomingAddress, txHash), ())
          })
        } yield Some(incomingAddress)
      })
      _ <- EitherT.right[String](transaction.value.outputs.toList.traverse{ case (address, _) =>
        if (Option(address) === incomingAddressOption) Monad[F].pure(())
        else addressTransactionIndex.put((address, txHash), ())
      })
      _ <- transaction.value.ticketData.flatMap(_.license).traverse { license =>
        EitherT.right[String](licenseTransactionIndex.put((license, txHash), ()))
      }
    } yield ()

    def listByAddress(address: Address, offset: Int, limit: Int): EitherT[F, String, List[UInt256Bytes]] =
      addressTransactionIndex.from((address, UInt256Refine.EmptyBytes), offset, limit).map(_.map{
        case ((address@_, txHash), ()) => txHash
      })

    def listByLicense(license: String, offset: Int, limit: Int): EitherT[F, String, List[UInt256Bytes]] =
      licenseTransactionIndex.from((license, UInt256Refine.EmptyBytes), offset, limit).map(_.map{
        case ((license@_, txHash), ()) => txHash
      })
  }
}
