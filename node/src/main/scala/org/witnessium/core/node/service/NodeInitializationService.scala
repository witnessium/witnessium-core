package org.witnessium.core
package node
package service

import cats.Monad
import cats.data.EitherT
import cats.effect.Concurrent

import crypto.Hash.ops._
import datatype.UInt256Bytes
import model.{Block, Transaction}
import repository.{BlockRepository, TransactionRepository}

object NodeInitializationService {

  def putGenesisBlockAndTransaction[F[_]: Concurrent: BlockRepository: TransactionRepository](
    genesisBlock: Block,
    genesisTransaction: Transaction.Verifiable,
  ): EitherT[F, String, Unit] = (for {
    _ <- implicitly[BlockRepository[F]].put(genesisBlock)
    _ <- implicitly[TransactionRepository[F]].put(genesisTransaction)
  } yield ())

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def checkSavedGenesis[F[_]: Monad: BlockRepository](
    blockNumberFrom: BigInt,
    blockHashFrom: UInt256Bytes,
    genesisBlock: Block,
  ): EitherT[F, String, Unit] = for {
    currentBlockOption <- implicitly[BlockRepository[F]].get(blockHashFrom)
    currentBlock <- EitherT.fromOption[F](currentBlockOption, s"Not found block #$blockNumberFrom: $blockHashFrom")
    result <- {
      if (blockNumberFrom > BigInt(0))
        checkSavedGenesis(blockNumberFrom - 1, currentBlock.header.parentHash, genesisBlock)
      else if (currentBlock === genesisBlock)
        EitherT.pure[F, String](())
      else
        EitherT.leftT[F, Unit](s"Expected genesis block $genesisBlock, but found $currentBlock")
    }
  } yield result

  def initialize[F[_]: Concurrent: Monad: BlockRepository: TransactionRepository] (
    genesisBlock:Block,
    genesisTransaction: Transaction.Verifiable,
  ): EitherT[F, String, Unit] = for {
    bestBlockHeaderOption <- implicitly[BlockRepository[F]].bestHeader
    _ <- bestBlockHeaderOption match {
      case None =>
        putGenesisBlockAndTransaction(genesisBlock, genesisTransaction)
      case Some(bestBlockHeader) =>
        checkSavedGenesis(bestBlockHeader.number.value, bestBlockHeader.toHash, genesisBlock)
    }
  } yield ()
}
