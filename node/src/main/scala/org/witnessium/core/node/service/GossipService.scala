package org.witnessium.core
package node
package service

import model.NodeStatus

trait GossipService[F[_]] {
  def status: F[Either[String, NodeStatus]]
}
