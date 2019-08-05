package org.witnessium.core
package node.service

import cats.effect.IO
import model.GossipMessage

trait GossipMessagePublisher {
  def listen(listener: GossipMessage => IO[Unit]): Unit
}
