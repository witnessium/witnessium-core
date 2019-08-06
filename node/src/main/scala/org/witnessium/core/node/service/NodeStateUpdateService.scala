package org.witnessium.core
package node.service

import model.GossipMessage

trait NodeStateUpdateService[F[_]] {
  def onGossip(message: GossipMessage): F[Unit]
}
