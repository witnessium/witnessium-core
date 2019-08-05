package org.witnessium.core
package node
package repository
package interpreter

import cats.data.EitherT
import scodec.bits.ByteVector
import swaydb._
import swaydb.data.IO

import codec.byte.{ByteDecoder, ByteEncoder}
import datatype.UInt256Bytes
import model.Transaction
import util.SwayIOCats._

class TransactionRepositoryInterpreter(swayMap: Map[Array[Byte], Array[Byte], IO]) extends TransactionRepository[IO] {

  def get(transactionHash: UInt256Bytes): IO[Either[String, Transaction.Verifiable]] = (for {
    array <- EitherT.fromOptionF(
      swayMap.get(transactionHash.toBytes.toArray),
      s"Does not exist transaction with hash $transactionHash"
    )
    decodeResult <- EitherT.fromEither[IO](ByteDecoder[Transaction.Verifiable].decode(ByteVector.view(array)))
  } yield decodeResult.value).value

  def put(verifiableTransaction: Transaction.Verifiable): IO[Either[String, Unit]] = (for{
    bytes <- EitherT.pure[IO, String](ByteEncoder[Transaction.Verifiable].encode(verifiableTransaction))
    hash = crypto.hash(verifiableTransaction.value)
    _ <- EitherT.liftF[IO, String, Unit](swayMap.put(hash.toArray, bytes.toArray).map(_ => ()))
  } yield ()).value

  def removeWithHash(transactionHash: UInt256Bytes): IO[Unit] = removeWithHashArray(transactionHash.toArray)

  private def removeWithHashArray(hashArray: Array[Byte]): IO[Unit] = swayMap.remove(hashArray).map(_ => ())

  def remove(signedTransaction: Transaction.Verifiable): IO[Unit] = {
    val byteArray = ByteEncoder[Transaction].encode(signedTransaction.value).toArray
    val hash = crypto.keccak256(byteArray)
    removeWithHashArray(hash)
  }

  def close(): IO[Unit] = swayMap.closeDatabase()

}
