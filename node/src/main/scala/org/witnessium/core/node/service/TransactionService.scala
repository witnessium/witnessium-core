package org.witnessium.core
package node.service

import datatype.UInt256Refine
import model.Transaction

trait TransactionService[F[_]] extends GossipMessagePublisher {
  def submit(transaction: Transaction): F[Either[String, UInt256Refine.UInt256Bytes]] = ???
  def stop(): F[Unit] = ???
}
