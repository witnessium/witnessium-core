package org.witnessium.core
package node.service

trait PeerConnectionService[F[_]] extends GossipMessagePublisher[F] {
  def stop(): F[Unit]
}
