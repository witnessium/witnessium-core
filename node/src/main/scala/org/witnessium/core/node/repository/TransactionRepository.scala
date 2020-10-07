package org.witnessium.core
package node
package repository

import cats.Monad
import cats.data.EitherT
import cats.implicits._

import crypto._
import crypto.Hash.ops._
import datatype.{UInt256Bytes, UInt256Refine}
import model.{Address, Genesis, Signed, MyGarageData, Transaction}
import store.{HashStore, StoreIndex}

trait TransactionRepository[F[_]] {
  def get(transactionHash: UInt256Bytes): EitherT[F, String, Option[Transaction.Verifiable]]
  def put(transaction: Transaction.Verifiable): EitherT[F, String, Unit]

  def buildIndex(transaction: Transaction.Verifiable): F[Unit]
  def listByAddress(address: Address, offset: Int, limit: Int): EitherT[F, String, List[UInt256Bytes]]
  def listVehicleTxHashes: EitherT[F, String, List[UInt256Bytes]]
  def listVehicleTxByVin(vin: String, offset: Int, limit: Int): EitherT[F, String, List[UInt256Bytes]]
  def listPartTxHashes: EitherT[F, String, List[UInt256Bytes]]
  def listPartTxByPartNo(partNo: String, offset: Int, limit: Int): EitherT[F, String, List[UInt256Bytes]]
}

object TransactionRepository {

  def apply[F[_]](implicit tr: TransactionRepository[F]): TransactionRepository[F] = tr

  def fromStores[F[_]: Monad](
    transctionHashStore: HashStore[F, Transaction.Verifiable],
    addressTransactionIndex: StoreIndex[F, (Address, UInt256Bytes), Unit],
    vehicleTransactionIndex: StoreIndex[F, (String, UInt256Bytes), Unit],
    partTransactionIndex: StoreIndex[F, (String, UInt256Bytes), Unit],
  ): TransactionRepository[F] = new TransactionRepository[F] {

    def get(transactionHash: UInt256Bytes): EitherT[F,String,Option[Transaction.Verifiable]] =
      transctionHashStore.get(transactionHash)

    def put(transaction: Transaction.Verifiable): EitherT[F,String,Unit] = for {
      _ <- transctionHashStore.put(transaction)
      _ <- EitherT.right[String](buildIndex(transaction))
    } yield ()

    def buildIndex(transaction: Transaction.Verifiable): F[Unit] = {
      val txHash = transaction.toHash
      val incomingAddressOption: Option[Address] = (transaction match {
        case Genesis(_) => None
        case Signed(sig, value@_) => sig.signedMessageHashToKey(transaction.toHash).map {
          publicKey => Address.fromPublicKeyHash(publicKey.toHash)
        }.toOption
      })
      val outputAddresses: Set[Address] = transaction.value.outputs.map(_._1)
      val addressSet: Set[Address] = outputAddresses ++ incomingAddressOption.toList
      val dataOption: Option[MyGarageData] = transaction.value.data

      for {
        _ <- addressSet.toList.traverse{ address =>
          addressTransactionIndex.put((address, txHash), ())
        }
        _ <- dataOption.traverse{
          case v: MyGarageData.Vehicle => vehicleTransactionIndex.put((v.vin, txHash), ())
          case p: MyGarageData.Part => partTransactionIndex.put((p.partNo, txHash), ())
        }
      } yield ()
    }

    def listByAddress(address: Address, offset: Int, limit: Int): EitherT[F, String, List[UInt256Bytes]] =
      addressTransactionIndex.from((address, UInt256Refine.EmptyBytes), offset, limit).map{ list =>
        for {
          ((address1, txHash), ()) <- list if address1 === address
        } yield txHash
      }

    def listVehicleTxHashes: EitherT[F, String, List[UInt256Bytes]] =
      vehicleTransactionIndex.from(("", UInt256Refine.EmptyBytes), 0, Int.MaxValue).map(_.map{
        case ((vin@_, txHash), ()) => txHash
      })

    def listVehicleTxByVin(vin: String, offset: Int, limit: Int): EitherT[F, String, List[UInt256Bytes]] =
      vehicleTransactionIndex.from((vin, UInt256Refine.EmptyBytes), offset, limit).map(_.map{
        case ((vin@_, txHash), ()) => txHash
      })

    def listPartTxHashes: EitherT[F, String, List[UInt256Bytes]] =
      partTransactionIndex.from(("", UInt256Refine.EmptyBytes), 0, Int.MaxValue).map(_.map{
        case ((partNo@_, txHash), ()) => txHash
      })

    def listPartTxByPartNo(partNo: String, offset: Int, limit: Int): EitherT[F, String, List[UInt256Bytes]] =
      partTransactionIndex.from((partNo, UInt256Refine.EmptyBytes), offset, limit).map(_.map{
        case ((partNo@_, txHash), ()) => txHash
      })
  }
}
