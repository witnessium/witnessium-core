package org.witnessium.core
package node.service

trait BlockSuggestionService[F[_]] extends GossipMessagePublisher[F] {
  def stop(): F[Unit]
}
