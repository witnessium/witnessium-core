package org.witnessium.core
package node
package service

import datatype.UInt256Bytes
import model.{Block, GossipMessage, NodeStatus, State}
import p2p.BloomFilter

trait PeerConnectionService[F[_]] {
  def bestStateAndBlock(localStatus: NodeStatus): F[Either[String, Option[(State, Block)]]]
  def block(blockHash: UInt256Bytes): F[Option[Block]]
  def gossip(bloomFilter: BloomFilter): F[Either[String, GossipMessage]]
}
