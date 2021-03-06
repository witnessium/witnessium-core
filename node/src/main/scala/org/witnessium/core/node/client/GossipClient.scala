package org.witnessium.core
package node
package client

import datatype.UInt256Bytes
import model.{Block, GossipMessage, NodeStatus, Transaction}
import p2p.BloomFilter

trait GossipClient[F[_]] {

  def status: F[Either[String, NodeStatus]]

  def bloomfilter(bloomfilter: BloomFilter): F[Either[String, GossipMessage]]

  def unknownTransactions(transactionHashes: Seq[UInt256Bytes]): F[Either[String, Seq[Transaction.Verifiable]]]

  def block(blockHash: UInt256Bytes): F[Either[String, Option[Block]]]

  def close(): F[Unit]
}
