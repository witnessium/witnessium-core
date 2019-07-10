package org.witnessium.core
package node
package client

import model.NodeStatus

trait GossipClient[F[_]] {

  def status: F[Either[String, NodeStatus]]

  def close(): F[Unit]
}
