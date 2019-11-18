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
import repository.{BlockRepository, StateRepository, TransactionRepository}
import util.SwayIOCats._

class NodeInitializationServiceInterpreter(
  genesisBlockSetupService: GenesisBlockSetupService[SwayIO],
  localGossipService: LocalGossipService[IO],
  peerConnectionService: PeerConnectionService[Future],
  stateRepository: StateRepository[SwayIO],
  transactionRepository: TransactionRepository[SwayIO],
  blockRepository: BlockRepository[SwayIO],
) {//extends NodeInitializationService[IO] {

  def initialize: IO[Either[String, Unit]] = {
    for {
      _ <- EitherT.right[String](genesisBlockSetupService().toIO)
      localStatus <- EitherT(localGossipService.status.toIO)
      stateAndBlockOption <- EitherT(peerConnectionService.bestStateAndBlock(localStatus).toIO)
      block <- stateAndBlockOption match {
        case None => for {
          blockOption <- EitherT(localGossipService.block(localStatus.bestHash).toIO)
          block <- EitherT.fromOption[IO](blockOption, s"Cannot found local block ${localStatus.bestHash}")
        } yield block

        case Some((state, block)) => for {
          _ <- EitherT.right[String](state.unused.toList.traverse{
            case (address, txHash) => stateRepository.put(address, txHash)
          }.toIO)
          _ <- EitherT.right[String](state.transactions.toList.traverse{transactionRepository.put(_).value}.toIO)
        } yield block
      }
      _ <- EitherT(loop(block))
    } yield ()
  }.value

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def loop(currentBlock: Block): IO[Either[String, Unit]] = IO.suspend{
    if (currentBlock.header.number.value === BigInt(0)) EitherT.rightT[IO, String](()).value else {
      blockRepository.put(currentBlock).value.toIO *> IO.cancelBoundary *> (for {
        localBlockOption <- EitherT(localGossipService.block(currentBlock.header.parentHash).toIO)
        block <- EitherT.fromOptionF(OptionT.fromOption[IO](localBlockOption).orElse(
          OptionT(peerConnectionService.block(currentBlock.header.parentHash).toIO)
        ).value, s"Parent block not found: $currentBlock")
        _ <- EitherT(loop(block))
      } yield ()).value
    }
  }
}
