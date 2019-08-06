package org.witnessium.core
package node
package service
package interpreter

import cats.effect.IO
import cats.implicits._
import swaydb.data.{IO => SwayIO}
import model.GossipMessage
import repository.GossipRepository
import util.SwayIOCats._

class NodeStateUpdateServiceInterpreter(
  gossipRepository: GossipRepository[SwayIO],
) extends NodeStateUpdateService[IO] {
  def onGossip(message: GossipMessage): IO[Unit] = {
    message.blockSuggestions.toList.traverse{ case (blockHeader, transactionHashes) =>
      gossipRepository.putNewBlockSuggestion(blockHeader, transactionHashes)
    } *>
    message.blockVotes.toList.traverse{ case (blockHash, transactionHashes) =>
      transactionHashes.toList.traverse{ gossipRepository.putNewBlockVote(blockHash, _) }
    } *>
    message.newTransactions.toList.traverse(gossipRepository.putNewTransaction)
  }.toIO *> IO.unit
}
