package org.witnessium.core
package node
package client

import datatype.UInt256Bytes
import model.{Block, GossipMessage, NodeStatus, State, Transaction}
import p2p.BloomFilter

trait GossipClient[F[_]] {

  def status: F[Either[String, NodeStatus]]

  def bloomfilter(bloomfilter: BloomFilter): F[Either[String, GossipMessage]]

  def unknownTransactions(transactionHashes: Seq[UInt256Bytes]): F[Either[String, Seq[Transaction.Signed]]]

  def state(stateRoot: UInt256Bytes): F[Either[String, State]]

  def block(blockHash: UInt256Bytes): F[Either[String, Block]]

  def close(): F[Unit]
}
