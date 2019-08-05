package org.witnessium.core
package node
package service

import datatype.UInt256Bytes
import model.{Block, GossipMessage, NodeStatus, State, Transaction}
import p2p.BloomFilter

trait LocalGossipService[F[_]] {
  def status: F[Either[String, NodeStatus]]

  def bloomfilter(bloomfilter: BloomFilter): F[Either[String, GossipMessage]]

  def unknownTransactions(transactionHashes: Seq[UInt256Bytes]): F[Either[String, Seq[Transaction.Verifiable]]]

  def state(stateRoot: UInt256Bytes): F[Either[String, Option[State]]]

  def block(blockHash: UInt256Bytes): F[Either[String, Option[Block]]]
}
