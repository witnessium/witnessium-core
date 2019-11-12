package org.witnessium.core
package node
package repository
package interpreter

import cats.data.{EitherT, OptionT}
import cats.implicits._
import scodec.bits.ByteVector
import swaydb.Map
import swaydb.data.IO

import codec.byte.{ByteDecoder, ByteEncoder}
import datatype.UInt256Bytes
import model.{Block, BlockHeader, Signature}
import util.SwayIOCats._

class BlockRepositoryInterpreter(
  swayBestHeaderMap: Map[Array[Byte], Array[Byte], IO],
  swayHeaderMap: Map[Array[Byte], Array[Byte], IO],
  swayTransactionsMap: Map[Array[Byte], Array[Byte], IO],
  swaySignaturesMap: Map[Array[Byte], Array[Byte], IO],
) extends BlockRepository[IO] {

  private val BestHeaderKey: Array[Byte] = Array.fill(32)(0.toByte)

  def get(blockHash: UInt256Bytes): EitherT[IO, String, Option[Block]] = (for {
    header <- OptionT(getHeader(blockHash))
    txHashes <-OptionT.liftF(getTransactionHashes(blockHash))
    votes <- OptionT.liftF(getSignatures(blockHash))
  } yield Block(header, txHashes.toSet, votes.toSet)).value

  def getHeader(blockHash: UInt256Bytes): EitherT[IO, String, Option[BlockHeader]] = for {
    arrayOption <- EitherT.right(swayHeaderMap.get(blockHash.toBytes.toArray))
    decodeResult <- arrayOption.traverse{ array =>
      EitherT.fromEither[IO](
        ByteDecoder[BlockHeader].decode(ByteVector.view(array))
      )
    }
  } yield decodeResult.map(_.value)

  def bestHeader: EitherT[IO, String, BlockHeader] = (for {
    arrayOption <- EitherT.right(swayBestHeaderMap.get(BestHeaderKey))
    decodeResult <- arrayOption.traverse{ array =>
      EitherT.fromEither[IO](
        ByteDecoder[BlockHeader].decode(ByteVector.view(array))
      )
    }
  } yield decodeResult.map(_.value)).flatMap{ headerOption =>
    EitherT.fromOption(headerOption, "Do not exist best block header")
  }

  def getTransactionHashes(blockHash: UInt256Bytes): EitherT[IO, String, Seq[UInt256Bytes]] = EitherT{
    swayTransactionsMap.get(blockHash.toArray).map { bytesOption =>
      for {
        bytes <- bytesOption.toRight(s"Do not exist block transactions: $blockHash")
        decoded <- ByteDecoder[List[UInt256Bytes]].decode(ByteVector.view(bytes))
      } yield decoded.value
    }
  }

  def getSignatures(blockHash: UInt256Bytes): EitherT[IO, String, Seq[Signature]] = EitherT{
    swaySignaturesMap.get(blockHash.toArray).map { bytesOption =>
      for {
        bytes <- bytesOption.toRight(s"Do not exist block signatures: $blockHash")
        decoded <- ByteDecoder[List[Signature]].decode(ByteVector.view(bytes))
      } yield decoded.value
    }
  }

  def put(block: Block): EitherT[IO, String, Unit] = {
    val blockHeaderArray = ByteEncoder[BlockHeader].encode(block.header).toArray
    val blockHash = crypto.keccak256(blockHeaderArray)
    EitherT.right[String](for {
      bestHeaderEither <- bestHeader.value
      _ <- bestHeaderEither match {
        case Right(bestHeaderValue) if block.header.number.value <= bestHeaderValue.number.value => IO.unit
        case _ => swayBestHeaderMap.put(BestHeaderKey, blockHeaderArray)
      }
      _ <- swayHeaderMap.put(blockHash, blockHeaderArray)
      _ <- swayTransactionsMap.put(blockHash,
        ByteEncoder[List[UInt256Bytes]].encode(block.transactionHashes.toList).toArray)
      _ <- swaySignaturesMap.put(blockHash,
        ByteEncoder[List[Signature]].encode(block.votes.toList).toArray)
    } yield ())
  }

  def close(): IO[Unit] = for {
    _ <- swayHeaderMap.closeDatabase()
    _ <- swayTransactionsMap.closeDatabase()
    _ <- swaySignaturesMap.closeDatabase()
  } yield ()
}
