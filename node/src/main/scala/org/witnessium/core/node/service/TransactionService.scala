package org.witnessium.core
package node.service

import datatype.UInt256Bytes
import model.Transaction

trait TransactionService[F[_]] extends GossipMessagePublisher {
  def submit(transaction: Transaction.Signed): F[Either[String, UInt256Bytes]] = ???
  def stop(): F[Unit] = ???
}
