package org.witnessium.core
package node
package service

import cats.Monad
import cats.data.EitherT
import cats.effect.Concurrent

import crypto.Hash.ops._
import crypto.MerkleTrie.MerkleTrieState
import datatype.{MerkleTrieNode, UInt256Bytes}
import model.{Block, Transaction}
import repository.{BlockRepository, StateRepository, TransactionRepository}
import StateRepository._
import store.HashStore

object NodeInitializationService {

  def putGenesisBlockAndTransaction[F[_]: Concurrent: BlockRepository: TransactionRepository](
    genesisBlock: Block,
    genesisState: MerkleTrieState,
    genesisTransaction: Transaction.Verifiable,
  )(implicit hashStore: HashStore[F, MerkleTrieNode]): EitherT[F, String, Unit] = (for {
    _ <- implicitly[BlockRepository[F]].put(genesisBlock)
    _ <- StateRepository.put[F](genesisState)
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

  def initialize[F[_]: Concurrent: BlockRepository: TransactionRepository] (
    genesisBlock: Block,
    genesisState: MerkleTrieState,
    genesisTransaction: Transaction.Verifiable,
  )(implicit hashStore: HashStore[F, MerkleTrieNode]): EitherT[F, String, Unit] = for {
    bestBlockHeaderOption <- implicitly[BlockRepository[F]].bestHeader
    _ <- bestBlockHeaderOption match {
      case None =>
        putGenesisBlockAndTransaction(genesisBlock, genesisState, genesisTransaction)
      case Some(bestBlockHeader) =>
        checkSavedGenesis(bestBlockHeader.number.value, bestBlockHeader.toHash, genesisBlock)
    }
    bestBlockHeaderOption2 <- implicitly[BlockRepository[F]].bestHeader
    bestBlockHeader <- EitherT.fromOption[F](bestBlockHeaderOption2, s"Empty best block header")
    allStates <- MerkleTrieState.fromRoot(bestBlockHeader.stateRoot).getAll
  } yield {
    allStates.foreach{ case (address, txHash) =>
      scribe.info(s"Current state: $address : $txHash")
    }
  }
}
