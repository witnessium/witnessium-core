package org.witnessium.core
package node
package service
package interpreter

import cats.data.{EitherT, OptionT}
import cats.effect.IO
import swaydb.data.{IO => SwayIO}
import datatype.UInt256Bytes
import model.{Block, GossipMessage, NetworkId, NodeStatus, State, Transaction}
import repository.{BlockRepository, GossipRepository}
import p2p.BloomFilter
import util.SwayIOCats._

class LocalGossipServiceInterpreter(
  networkId: NetworkId,
  blockRepository: BlockRepository[SwayIO],
  gossipRepository: GossipRepository[SwayIO],
) extends LocalGossipService[IO] {

  override def status: IO[Either[String, NodeStatus]] = (for {
    bestBlockHeader <- EitherT(blockRepository.bestHeader)
  } yield NodeStatus(
    networkId = networkId,
    genesisHash = gossipRepository.genesisHash,
    bestHash = crypto.hash(bestBlockHeader),
    number = bestBlockHeader.number,
  )).value.toIO

  override def bloomfilter(bloomfilter: BloomFilter): IO[Either[String, GossipMessage]] = ???

  override def unknownTransactions(
    transactionHashes: Seq[UInt256Bytes]
  ): IO[Either[String, Seq[Transaction.Verifiable]]] = ???

  override def state(stateRoot: UInt256Bytes): IO[Either[String, Option[State]]] = ???

  override def block(blockHash: UInt256Bytes): IO[Either[String, Option[Block]]] = (for {
    blockHeader <- OptionT(EitherT(blockRepository.getHeader(blockHash)))
    transactionHashes <- OptionT.liftF(EitherT(blockRepository.getTransactionHashes(blockHash)))
    votes <- OptionT.liftF(EitherT(blockRepository.getSignatures(blockHash)))
  } yield Block(blockHeader, transactionHashes.toSet, votes.toSet)).value.value.toIO
}
