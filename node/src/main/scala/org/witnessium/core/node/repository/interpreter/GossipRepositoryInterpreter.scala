package org.witnessium.core
package node
package repository
package interpreter

import cats.data.EitherT
import cats.effect.concurrent.Ref
import cats.implicits._
import scodec.bits.ByteVector
import swaydb.Map
import swaydb.data.IO

import codec.byte.{ByteDecoder, ByteEncoder}
import datatype.UInt256Bytes
import model.{BlockHeader, Signature, Transaction}
import util.SwayIOCats._

class GossipRepositoryInterpreter(
  genesisHashRef: Ref[IO, UInt256Bytes],
  swayBlockSuggestionMap: Map[Array[Byte], Array[Byte], IO],
  swayBlockVoteMap: Map[Array[Byte], Array[Byte], IO],
  swayNewTransactionMap: Map[Array[Byte], Array[Byte], IO],
) extends GossipRepository[IO] {

  def genesisHash_= (hash: UInt256Bytes): Unit = genesisHashRef.set(hash).get

  def genesisHash: UInt256Bytes = genesisHashRef.get.get

  def blockSuggestions(): IO[Either[String, Set[BlockSuggestion]]] = {
    swayBlockSuggestionMap
      .map(_._2)
      .materialize
      .map(_.toList.traverse[Either[String, *], BlockSuggestion]{ byteArray =>
        val byteVector = ByteVector.view(byteArray)
        ByteDecoder[BlockSuggestion].decode(byteVector).filterOrElse(
          _.remainder.isEmpty,
          s"Non empty bytes after decoding block suggestion: $byteVector"
        ).map(_.value)
      }.map(_.toSet))
  }

  def blockSuggestion(blockHash: UInt256Bytes): IO[Either[String, Option[BlockSuggestion]]] = {
    swayBlockVoteMap.get(blockHash.toArray).map{ byteArrayOption =>
      byteArrayOption.traverse[Either[String, *], BlockSuggestion]{ byteArray =>
        val byteVector = ByteVector.view(byteArray)
        ByteDecoder[BlockSuggestion].decode(byteVector).filterOrElse(
          _.remainder.isEmpty,
          s"Non empty bytes after decoding block suggestion: $byteVector"
        ).map(_.value)
      }
    }
  }

  def blockVotes(blockHash: UInt256Bytes): IO[Either[String, Set[Signature]]] = {
    val byteArray = blockHash.toArray

    swayBlockVoteMap
      .keys
      .fromOrAfter(byteArray)
      .takeWhile(_ startsWith byteArray)
      .materialize
      .map{ blockHashAndSignatures =>
        blockHashAndSignatures.toList.traverse[Either[String, *], Signature]{ blockHashAndSignature =>
          val byteVector = ByteVector.view(blockHashAndSignature drop byteArray.size)
          ByteDecoder[Signature].decode(byteVector).filterOrElse(
            _.remainder.isEmpty,
            s"Non empty bytes after decoding signature: $byteVector"
          ).map(_.value)
        }.map(_.toSet)
      }
  }

  def newTransactions: IO[Either[String, Set[Transaction.Verifiable]]] = {
    swayNewTransactionMap
      .map(_._2)
      .materialize
      .map(_.toList.traverse[Either[String, *], Transaction.Verifiable]{ byteArray =>
        val byteVector = ByteVector.view(byteArray)
        ByteDecoder[Transaction.Verifiable].decode(byteVector).filterOrElse(
          _.remainder.isEmpty,
          s"Non empty bytes after decoding new transaction: $byteVector"
        ).map(_.value)
      }.map(_.toSet))
  }

  def newTransaction(transactionHash: UInt256Bytes): IO[Either[String, Option[Transaction.Verifiable]]] = {
    swayNewTransactionMap.get(transactionHash.toBytes.toArray).map{ byteArrayOption =>
      byteArrayOption.traverse[Either[String, *], Transaction.Verifiable]{ byteArray =>
        val byteVector = ByteVector.view(byteArray)
        ByteDecoder[Transaction.Verifiable].decode(byteVector).filterOrElse(
          _.remainder.isEmpty,
          s"Non empty bytes after decoding block votes: $byteVector"
        ).map(_.value)
      }
    }
  }

  def putNewBlockSuggestion(header: BlockHeader, transactionHashes: Set[UInt256Bytes]): IO[Unit] = {
    val headerBytes = ByteEncoder[BlockHeader].encode(header)
    val transactionHashesBytes = ByteEncoder[Set[UInt256Bytes]].encode(transactionHashes)
    val hash = crypto.keccak256(headerBytes.toArray)

    swayBlockSuggestionMap.put(hash, (headerBytes ++ transactionHashesBytes).toArray).map(_ => ())
  }

  def putNewTransaction(transaction: Transaction.Verifiable): IO[Unit] = {
    val transactionBytes = ByteEncoder[Transaction.Verifiable].encode(transaction)
    val hash = crypto.keccak256(transactionBytes.toArray)

    swayNewTransactionMap.put(hash, transactionBytes.toArray).map(_ => ())
  }

  def putNewBlockVote(blockHash: UInt256Bytes, signature: Signature): IO[Unit] = swayBlockVoteMap.put(
    key = (blockHash ++ ByteEncoder[Signature].encode(signature)).toArray,
    value = Array.empty
  ).map(_ => ())

  def finalizeBlock(blockhash: UInt256Bytes): IO[Either[String, Unit]] = (for {
    suggestionOption <- EitherT(blockSuggestion(blockhash))
    suggestion <- EitherT.fromOption[IO](suggestionOption, s"Block not found: $blockhash")
    blockNumber = suggestion._1.number
    savedSuggestions <- EitherT(blockSuggestions())
    targetBlockHeaders = for {
      (header, _) <- savedSuggestions if header.number.value <= blockNumber.value
      hash = crypto.keccak256(ByteEncoder[BlockHeader].encode(header).toArray)
    } yield hash
    _ <- EitherT.right[String](swayBlockSuggestionMap.remove(targetBlockHeaders).map(_ => ()))
    transactionHashArrays = suggestion._2.map(_.toArray)
    _ <- EitherT.right[String](swayNewTransactionMap.remove(transactionHashArrays).map(_ => ()))
  } yield ()).value

  def close(): IO[Unit] = for {
    _ <- swayBlockSuggestionMap.closeDatabase()
    _ <- swayBlockVoteMap.closeDatabase()
    _ <- swayNewTransactionMap.closeDatabase()
  } yield ()
}
