package org.witnessium.core
package node
package service
package interpreter

import cats.effect.IO
import eu.timepit.refined.refineMV
import eu.timepit.refined.numeric.NonNegative
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
    bestBlockHeader <- blockRepository.bestHeader
    genesisHash = gossipRepository.genesisHash
  } yield NodeStatus(
    networkId = networkId,
    genesisHash = genesisHash,
    bestHash = bestBlockHeader.fold(genesisHash)(crypto.hash),
    number = bestBlockHeader.fold(refineMV[NonNegative](BigInt(0)))(_.number),
  )).value.toIO

  override def bloomfilter(bloomfilter: BloomFilter): IO[Either[String, GossipMessage]] = ???

  override def unknownTransactions(
    transactionHashes: Seq[UInt256Bytes]
  ): IO[Either[String, Seq[Transaction.Verifiable]]] = ???

  override def state(stateRoot: UInt256Bytes): IO[Either[String, Option[State]]] = ???

  override def block(blockHash: UInt256Bytes): IO[Either[String, Option[Block]]] = blockRepository.get(blockHash).value.toIO
}
