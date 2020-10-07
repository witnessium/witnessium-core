package org.witnessium.core
package node
package service

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import shapeless.syntax.typeable._
import datatype.UInt256Bytes
import model.{MyGarageData, Transaction}
import repository.TransactionRepository

object MyGarageService {

  def listVehicle[F[_]:Monad:TransactionRepository]: EitherT[F, String, List[MyGarageData.Vehicle]] = for {
    txHashes <- TransactionRepository[F].listVehicleTxHashes
    vehicles <- txHashes.traverse{ (txHash: UInt256Bytes) =>
      TransactionRepository[F].get(txHash).map{ (txOption: Option[Transaction.Verifiable]) =>
        (for {
          tx <- txOption
          v <- tx.value.data.cast[MyGarageData.Vehicle]
        } yield v).toList
      }
    }
  } yield vehicles.flatten

  def getVehicle[F[_]:Monad:TransactionRepository](vin: String): EitherT[F, String, List[MyGarageData.Vehicle]] = for {
    txHashes <- TransactionRepository[F].listVehicleTxByVin(vin, 0, Int.MaxValue)
    vehicles <- txHashes.traverse{ (txHash: UInt256Bytes) =>
      TransactionRepository[F].get(txHash).map{ (txOption: Option[Transaction.Verifiable]) =>
        (for {
          tx <- txOption
          v <- tx.value.data.cast[MyGarageData.Vehicle]
        } yield v).toList
      }
    }
  } yield vehicles.flatten

  def listPart[F[_]:Monad:TransactionRepository]: EitherT[F, String, List[MyGarageData.Part]] = for {
    txHashes <- TransactionRepository[F].listPartTxHashes
    parts <- txHashes.traverse{ (txHash: UInt256Bytes) =>
      TransactionRepository[F].get(txHash).map{ (txOption: Option[Transaction.Verifiable]) =>
        (for {
          tx <- txOption
          p <- tx.value.data.cast[MyGarageData.Part]
        } yield p).toList
      }
    }
  } yield parts.flatten

  def getPart[F[_]:Monad:TransactionRepository](partNo: String): EitherT[F, String, List[MyGarageData.Part]] = for {
    txHashes <- TransactionRepository[F].listPartTxByPartNo(partNo, 0, Int.MaxValue)
    parts <- txHashes.traverse{ (txHash: UInt256Bytes) =>
      TransactionRepository[F].get(txHash).map{ (txOption: Option[Transaction.Verifiable]) =>
        (for {
          tx <- txOption
          p <- tx.value.data.cast[MyGarageData.Part]
        } yield p).toList
      }
    }
  } yield parts.flatten

}
