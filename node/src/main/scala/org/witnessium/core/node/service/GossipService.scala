package org.witnessium.core
package node
package service

import datatype.UInt256Bytes
import model.{GossipMessage, NodeStatus, Transaction}
import p2p.BloomFilter

trait GossipService[F[_]] {
  def status: F[Either[String, NodeStatus]]

  def bloomfilter(bloomfilter: BloomFilter): F[Either[String, GossipMessage]]

  def unknownTransactions(transactionHashes: Seq[UInt256Bytes]): F[Either[String, Seq[Transaction.Signed]]]
}
