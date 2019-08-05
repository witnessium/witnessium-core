package org.witnessium.core
package node
package service
package interpreter

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import com.twitter.util.Future
import swaydb.data.{IO => SwayIO}
import datatype.UInt256Bytes
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
      stateAndBlockHeaderOption <- EitherT(peerConnectionService.bestStateAndBlockHeader(localStatus).toIO)
      (state, blockHeader) <- EitherT.fromOption[IO](stateAndBlockHeaderOption, "Already synchronized with peers")
      _ <- EitherT.right[String](state.unused.toList.traverse{
        case (address, txHash) => stateRepository.put(address, txHash)
      }.toIO)
      _ <- EitherT(loop(crypto.hash(blockHeader)))
    } yield ()
  }.value

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def loop(currentBlockHash: UInt256Bytes): IO[Either[String, Unit]] = IO.suspend{
    (for {
      blockOption <- EitherT(localGossipService.block(currentBlockHash).toIO)
      block <- blockOption.map(EitherT.pure[IO, String](_)).getOrElse(
        EitherT(peerConnectionService.block(currentBlockHash).toIO)
      )
    } yield block).flatMap {
      case block if block.header.number.value === BigInt(0) => EitherT.rightT[IO, String](())
      case block => EitherT(
        blockRepository.put(block).toIO *> IO.cancelBoundary *> loop(block.header.parentHash)
      )
    }.value
  }
}
