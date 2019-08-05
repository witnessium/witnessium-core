package org.witnessium.core
package node
package service

import cats.effect.IO
import model.GossipMessage

class PeerGossipPullingService extends GossipMessagePublisher {
  def listen(listener: GossipMessage => IO[Unit]): Unit = ???
}
