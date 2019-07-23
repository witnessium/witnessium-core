package org.witnessium.core
package node
package client

import model.{GossipMessage, NodeStatus}
import p2p.BloomFilter

trait GossipClient[F[_]] {

  def status: F[Either[String, NodeStatus]]

  def bloomfilter(bloomfilter: BloomFilter): F[Either[String, GossipMessage]]

  def close(): F[Unit]
}
