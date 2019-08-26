package org.witnessium.core
package node
package service
package interpreter

import java.time.Instant
import scala.concurrent.duration._
import cats.data.EitherT
import cats.effect.{IO, Timer}
import cats.implicits._
import eu.timepit.refined.refineV
import eu.timepit.refined.numeric.NonNegative
import swaydb.data.{IO => SwayIO}
import crypto._
import model.{BlockHeader, GossipMessage}
import repository.{BlockRepository, GossipRepository}

class BlockSuggestionServiceInterpreter(
//  nodeNumber: Int,
//  peerSize: Int,
//  blockCreationPeriod: Int,
  localKeyPair: KeyPair,
  val gossipListener: GossipMessage => IO[Unit],
  blockRepository: BlockRepository[SwayIO],
  gossipRepository: GossipRepository[SwayIO],
) extends BlockSuggestionService[IO] {

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter", "org.wartremover.warts.Recursion"))
  def run(implicit timer: Timer[IO]): IO[Unit] = for {
    _ <- IO.sleep(2000.millis)
    _ <- IO(scribe.info(s"running block suggention service"))
    _ <- suggestBlock
    _ <- run
  } yield ()
//    second <- timer.clock.realTime(SECOND)
//    _ <- if (!isLeader(second)) IO.unit else {
//      for {
//        bestBlockHeader <- EitherT(blockRepository.bestHeader.toIO)
//        blockSuggestions <- EitherT(gossipRepository.blockSuggestions())
//        blockSuggestionsWithLocalVoteFlag <- blockSuggestions.toList.traverse{
//          case (blockHeader, _) => addLocallyVotedFlag(blockHeader)
//        }
//      } yield ()
//    }

//  def isLeader(second: Long): Boolean = ((second / blockCreationPeriod) % (peerSize + 1)).toInt === nodeNumber

//  def addLocallyVotedFlag(blockHeader: BlockHeader): EitherT[IO, String, (BlockHeader, Boolean)] = {
//    val blockHash = crypto.hash(blockHeader)
//    EitherT(gossipRepository.blockVotes(blockHash).toIO).map { votes =>
//      (blockHeader, votes.exists(_.signedMessageToKey(blockHash.toArray) === localKeyPair.publicKey))
//    }
//  }

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  def suggestBlock(implicit timer: Timer[IO]): IO[Either[String, Unit]] = (for {
    bestBlockHeader <- EitherT(blockRepository.bestHeader.toIO)
    _ <- EitherT.pure[IO, String](scribe.info(s"Best block header: $bestBlockHeader"))
    newTransactions <- EitherT(gossipRepository.newTransactions.toIO)
    transactionHashes = newTransactions.map{ newTransaction => crypto.hash(newTransaction.value) }
    _ <- if (newTransactions.isEmpty) EitherT.pure[IO, String]{
      scribe.info(s"No new transaction: $newTransactions")
    } else (for {
      _ <- EitherT.pure[IO, String](scribe.info(s"generating new block with transactions: $transactionHashes"))
      number <- EitherT.fromEither[IO](refineV[NonNegative](bestBlockHeader.number.value + 1))
      now <- EitherT.right[String](timer.clock.realTime(MILLISECONDS))
      newBlockHeader = BlockHeader(
        number = number,
        parentHash = crypto.hash(bestBlockHeader),
        stateRoot = crypto.hash(datatype.UInt256Refine.EmptyBytes),
        transactionsRoot = crypto.hash(transactionHashes.toList.sortBy(_.toHex)),
        timestamp = Instant.ofEpochMilli(now),
      )
      newBlockHash = crypto.hash(newBlockHeader)
      _ <- EitherT.pure[IO, String](scribe.info(s"next block header: $newBlockHeader"))
      signature <- EitherT.fromEither[IO](localKeyPair.sign(newBlockHash.toArray))
      gossipMessage = GossipMessage(
        blockSuggestions = Set((newBlockHeader, transactionHashes)),
        blockVotes = Map(newBlockHash -> Set(signature)),
        newTransactions = Set.empty,
      )
      _ <- EitherT.pure[IO, String](scribe.info(s"Block suggestion gossip message: $gossipMessage"))
      _ <- EitherT.right[String](gossipListener(gossipMessage))
    } yield ())
  } yield ()).value
}
