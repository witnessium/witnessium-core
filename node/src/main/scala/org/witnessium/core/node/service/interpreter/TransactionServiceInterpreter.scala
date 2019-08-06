package org.witnessium.core
package node.service
package interpreter

import cats.effect.IO
import datatype.UInt256Bytes
import model.{GossipMessage, Transaction}

class TransactionServiceInterpreter extends TransactionService[IO] {
  override def submit(transaction: Transaction.Signed): IO[Either[String, UInt256Bytes]] = ???
  override def listen(listener: GossipMessage => IO[Unit]): Unit = ???
  override def stop(): IO[Unit] = ???
}
