package org.witnessium.core
package node.service

import cats.effect.Timer

trait BlockSuggestionService[F[_]] extends GossipMessagePublisher {
  def run(implicit timer: Timer[F]): F[Unit]
}
