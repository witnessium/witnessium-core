package org.witnessium.core
package node.service

trait BlockSuggestionService[F[_]] extends GossipMessagePublisher {
  def stop(): F[Unit]
}
