package org.witnessium.core
package node
package repository
package interpreter

import scala.concurrent.{ExecutionContext, Future}
import cats.data.EitherT
import cats.implicits._
import scodec.bits.ByteVector
import swaydb._
import swaydb.data.IO

import codec.byte.{ByteDecoder, ByteEncoder}
import datatype.UInt256Bytes
import model.{Signature, Transaction}

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class TransactionRepositoryInterpreter(
  swayMap: Map[Array[Byte], Array[Byte], IO]
)(implicit ec: ExecutionContext) extends TransactionRepository[Future] {

  def get(transactionHash: UInt256Bytes): Future[Either[String, Transaction.Signed]] = (for {
    array <- EitherT.fromOptionF(
      swayMap.get(transactionHash.toBytes.toArray).toFuture,
      s"Does not exist transaction with hash $transactionHash"
    )
    decodeResult <- EitherT.fromEither[Future](ByteDecoder[Transaction.Signed].decode(ByteVector.view(array)))
  } yield decodeResult.value).value

  def put(signedTransaction: Transaction.Signed): Future[Either[String, Unit]] = (for{
    transactionBytes <- EitherT.pure[Future, String](ByteEncoder[Transaction].encode(signedTransaction.value))
    signatureBytes <- EitherT.pure[Future, String](ByteEncoder[Signature].encode(signedTransaction.signature))
    hash = crypto.keccak256(transactionBytes.toArray)
    _ <- EitherT.liftF[Future, String, Unit]{
      swayMap.put(hash, (transactionBytes ++ signatureBytes).toArray).toFuture.map(_ => ())
    }
  } yield ()).value

  def removeWithHash(transactionHash: UInt256Bytes): Future[Unit] =
    removeWithHashArray(transactionHash.toArray)

  private def removeWithHashArray(hashArray: Array[Byte]): Future[Unit] = {
    swayMap.remove(hashArray).map(_ => ()).toFuture
  }

  def remove(signedTransaction: Transaction.Signed): Future[Unit] = {
    val byteArray = ByteEncoder[Transaction].encode(signedTransaction.value).toArray
    val hash = crypto.keccak256(byteArray)
    removeWithHashArray(hash)
  }
}

