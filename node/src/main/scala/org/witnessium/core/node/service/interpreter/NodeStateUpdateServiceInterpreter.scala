package org.witnessium.core
package node
package service
package interpreter

import cats.effect.IO
import cats.implicits._
import swaydb.data.{IO => SwayIO}
import model.{Block, GossipMessage}
import repository.{BlockRepository, GossipRepository}
import util.SwayIOCats._

class NodeStateUpdateServiceInterpreter(
  blockRepository: BlockRepository[SwayIO],
  gossipRepository: GossipRepository[SwayIO],
) extends NodeStateUpdateService[IO] {
  def onGossip(message: GossipMessage): IO[Unit] = putNewGossip(message) *> {
    if (message.blockSuggestions.nonEmpty) putNewBlock(message) else IO.unit
  }

  def putNewGossip(message: GossipMessage): IO[Unit] = {
    message.blockSuggestions.toList.traverse{ case (blockHeader, transactionHashes) =>
      gossipRepository.putNewBlockSuggestion(blockHeader, transactionHashes)
    } *>
    message.blockVotes.toList.traverse{ case (blockHash, transactionHashes) =>
      transactionHashes.toList.traverse{ gossipRepository.putNewBlockVote(blockHash, _) }
    } *>
    message.newTransactions.toList.traverse(gossipRepository.putNewTransaction)
  }.toIO *> IO.unit


  def putNewBlock(message: GossipMessage): IO[Unit] = (for {
    (blockHeader, transactionHashes) <- message.blockSuggestions
    blockHash = crypto.hash(blockHeader)
    votes = message.blockVotes(blockHash)
  } yield (blockHash, Block(blockHeader, transactionHashes, votes))).toList.traverse {
    case (blockHash, block) => gossipRepository.finalizeBlock(blockHash) *> blockRepository.put(block)
  }.toIO *> IO.unit
}
