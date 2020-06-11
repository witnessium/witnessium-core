package org.witnessium.core
package node
package service
package interpreter

import cats.effect.IO
import crypto.Hash.ops._
import datatype.UInt256Bytes
import model.{GossipMessage, Transaction}

class TransactionServiceInterpreter(
  val gossipListener: GossipMessage => IO[Unit],
) {// extends TransactionService[IO] {
  def submit(transaction: Transaction.Signed): IO[UInt256Bytes] = {
    val gossipMessage = GossipMessage(
      blockSuggestions = Set.empty,
      blockVotes = Map.empty,
      newTransactions = Set[Transaction.Verifiable](transaction),
    )
    scribe.info(s"Generate new gossip message: $gossipMessage")
    gossipListener(gossipMessage).map(_ => transaction.value.toHash)
  }
}
