package org.witnessium.core
package node
package service

import model.{GossipMessage, NodeStatus}
import p2p.BloomFilter

trait GossipService[F[_]] {
  def status: F[Either[String, NodeStatus]]

  def bloomfilter(bloomfilter: BloomFilter): F[Either[String, GossipMessage]]
}
