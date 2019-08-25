package org.witnessium.core
package node
package service
package interpreter

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import swaydb.data.{IO => SwayIO}
import model.{Block, GossipMessage}
import repository.{BlockRepository, GossipRepository, TransactionRepository}
import util.SwayIOCats._

class NodeStateUpdateServiceInterpreter(
  blockRepository: BlockRepository[SwayIO],
  gossipRepository: GossipRepository[SwayIO],
  transactionRepository: TransactionRepository[SwayIO],
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
    case (blockHash, block) => (for {
      transactions <- block.transactionHashes.toList.traverse{
        hash => EitherT(gossipRepository.newTransaction(hash))
      }
      _ <- transactions.flatMap(_.toList).traverse{
        tx => EitherT(transactionRepository.put(tx))
      }
      _ <- EitherT.right[String](gossipRepository.putNewBlockSuggestion(block.header, block.transactionHashes))
      _ <- EitherT.right[String](gossipRepository.finalizeBlock(blockHash))
      _ <- EitherT.right[String](blockRepository.put(block))
    } yield ()).value
  }.toIO *> IO.unit
}
