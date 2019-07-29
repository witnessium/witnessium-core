package org.witnessium.core
package node.service

import model.GossipMessage

trait GossipMessagePublisher[F[_]] {
  def listen(listener: GossipMessage => F[Unit]): Unit
  def stop(): F[Unit]
}
