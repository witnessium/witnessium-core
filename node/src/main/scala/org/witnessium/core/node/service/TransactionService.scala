package org.witnessium.core
package node.service

import datatype.UInt256Refine
import model.{GossipMessage, Transaction}

trait TransactionService[F[_]] extends GossipMessagePublisher[F] {
  def submit(transaction: Transaction): F[Either[String, UInt256Refine.UInt256Bytes]] = ???
  def listen(listener: GossipMessage => F[Unit]): Unit = ???
  def stop(): F[Unit] = ???
}
