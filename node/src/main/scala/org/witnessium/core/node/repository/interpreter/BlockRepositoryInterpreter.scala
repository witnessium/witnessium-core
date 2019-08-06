package org.witnessium.core
package node
package repository
package interpreter

import cats.data.OptionT
import cats.implicits._
import scodec.bits.ByteVector
import swaydb.Map
import swaydb.data.IO

import codec.byte.{ByteDecoder, ByteEncoder}
import datatype.UInt256Bytes
import model.{Block, BlockHeader, Signature}

class BlockRepositoryInterpreter(
  swayBestHeaderMap: Map[Array[Byte], Array[Byte], IO],
  swayHeaderMap: Map[Array[Byte], Array[Byte], IO],
  swayTransactionsMap: Map[Array[Byte], Array[Byte], IO],
  swaySignaturesMap: Map[Array[Byte], Array[Byte], IO],
) extends BlockRepository[IO] {

  private val BestHeaderKey: Array[Byte] = Array.empty[Byte]

  def getHeader(blockHash: UInt256Bytes): IO[Either[String, Option[BlockHeader]]] = {
    swayHeaderMap.get(blockHash.toArray).map(bytesOption => (for {
      bytes <- OptionT.fromOption[Either[String, *]](bytesOption)
      decoded <- OptionT.liftF(ByteDecoder[BlockHeader].decode(ByteVector.view(bytes)))
    } yield decoded.value).value)
  }

  def bestHeader: IO[Either[String, BlockHeader]] = swayBestHeaderMap.get(BestHeaderKey).map{ bytesOption =>
    for {
      bytes <- bytesOption.toRight("Do not exist best block header")
      decoded <- ByteDecoder[BlockHeader].decode(ByteVector.view(bytes))
    } yield decoded.value
  }

  def getTransactionHashes(blockHash: UInt256Bytes): IO[Either[String, Seq[UInt256Bytes]]] = {
    swayTransactionsMap.get(blockHash.toArray).map { bytesOption =>
      for {
        bytes <- bytesOption.toRight(s"Do not exist block transactions: $blockHash")
        decoded <- ByteDecoder[List[UInt256Bytes]].decode(ByteVector.view(bytes))
      } yield decoded.value
    }
  }

  def getSignatures(blockHash: UInt256Bytes): IO[Either[String, Seq[Signature]]] = {
    swaySignaturesMap.get(blockHash.toArray).map { bytesOption =>
      for {
        bytes <- bytesOption.toRight(s"Do not exist block signatures: $blockHash")
        decoded <- ByteDecoder[List[Signature]].decode(ByteVector.view(bytes))
      } yield decoded.value
    }
  }

  def put(block: Block): IO[Unit] = {
    val blockHeaderArray = ByteEncoder[BlockHeader].encode(block.header).toArray
    val blockHash = crypto.keccak256(blockHeaderArray)
    for {
      bestHeaderEither <- bestHeader
      bestHeaderOption = bestHeaderEither.toOption
      _ <- bestHeaderOption.map{ (bestBlockHeader: BlockHeader) =>
        if (block.header.number.value > bestBlockHeader.number.value)
          swayBestHeaderMap.put(BestHeaderKey, blockHeaderArray)
        else IO.unit
      }.getOrElse(IO.unit)
      _ <- swayHeaderMap.put(blockHash, blockHeaderArray)
      _ <- swayTransactionsMap.put(blockHash,
        ByteEncoder[List[UInt256Bytes]].encode(block.transactionHashes.toList).toArray)
      _ <- swaySignaturesMap.put(blockHash,
        ByteEncoder[List[Signature]].encode(block.votes.toList).toArray)
    } yield ()
  }

  def close(): IO[Unit] = for {
    _ <- swayHeaderMap.closeDatabase()
    _ <- swayTransactionsMap.closeDatabase()
    _ <- swaySignaturesMap.closeDatabase()
  } yield ()
}
