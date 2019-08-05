package org.witnessium.core
package node
package service
package interpreter

import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits._
import com.twitter.util.Future
import swaydb.data.{IO => SwayIO}
import model.Block
import repository.{BlockRepository, StateRepository}
import util.SwayIOCats._

class NodeInitializationServiceInterpreter(
  localGossipService: LocalGossipService[SwayIO],
  peerConnectionService: PeerConnectionService[Future],
  stateRepository: StateRepository[SwayIO],
  blockRepository: BlockRepository[SwayIO],
) extends NodeInitializationService[IO] {

  override def initialize: IO[Either[String, Unit]] = {
    for {
      localStatus <- EitherT(localGossipService.status.toIO)
      stateAndBlockOption <- EitherT(peerConnectionService.bestStateAndBlock(localStatus).toIO)
      (state, block) <- EitherT.fromOption[IO](stateAndBlockOption, "Already synchronized with peers")
      _ <- EitherT.right[String](state.unused.toList.traverse{
        case (address, txHash) => stateRepository.put(address, txHash)
      }.toIO)
      _ <- EitherT(loop(block))
    } yield ()
  }.value

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def loop(currentBlock: Block): IO[Either[String, Unit]] = IO.suspend{
    if (currentBlock.header.number.value === BigInt(0)) EitherT.rightT[IO, String](()).value else {
      blockRepository.put(currentBlock).toIO *> IO.cancelBoundary *> (for {
        localBlockOption <- EitherT(localGossipService.block(currentBlock.header.parentHash).toIO)
        block <- EitherT.fromOptionF(OptionT.fromOption[IO](localBlockOption).orElse(
          OptionT(peerConnectionService.block(currentBlock.header.parentHash).toIO)
        ).value, s"Parent block not found: $currentBlock")
        _ <- EitherT(loop(block))
      } yield ()).value
    }
  }
}
