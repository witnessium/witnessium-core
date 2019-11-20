package org.witnessium.core
package node
package service

import java.time.Instant
import scala.concurrent.duration._
import cats.data.EitherT
import cats.effect.{Sync, Timer}
import eu.timepit.refined.refineV
import eu.timepit.refined.numeric.NonNegative

import crypto._
import crypto.MerkleTrie.MerkleTrieState
import crypto.KeyPair
import crypto.Hash.ops._
import datatype.{MerkleTrieNode, UInt256Bytes}
import model.{Address, Block, BlockHeader, Transaction}
import repository.{BlockRepository, StateRepository, TransactionRepository}
import repository.StateRepository._
import store.HashStore

object TransactionService {

  def addressFromSignedTransaction(transaction: Transaction.Signed): Either[String, Address] = for {
    (pubKey: BigInt) <- transaction.signature.signedMessageHashToKey(transaction.toHash)
  } yield Address.fromPublicKey(crypto.keccak256)(pubKey)

  def submit[F[_]: Timer: Sync: BlockRepository: TransactionRepository](
    transaction: Transaction.Signed,
    localKeyPair: KeyPair,
  )(implicit hashStore: HashStore[F, MerkleTrieNode]): EitherT[F, String, UInt256Bytes] = for {
    bestBlockHeaderOption <- implicitly[BlockRepository[F]].bestHeader
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"Best block header: $bestBlockHeaderOption")))
    bestBlockHeader <- EitherT.fromOption[F](bestBlockHeaderOption, "No best block header")
    txHash = transaction.toHash
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"generating new block with transactions: $txHash")))
    number <- EitherT.fromEither[F](refineV[NonNegative](bestBlockHeader.number.value + 1))
    now <- EitherT.right[String](Timer[F].clock.realTime(MILLISECONDS))
    fromAddress <- EitherT.fromEither[F](addressFromSignedTransaction(transaction))
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"From address: $fromAddress")))
    state <- MerkleTrieState.fromRoot(bestBlockHeader.stateRoot).put(fromAddress, transaction.value)
    stateRoot <- EitherT.fromOption[F](state.root, s"No root is found from $state")
    newBlockHeader = BlockHeader(
      number = number,
      parentHash = bestBlockHeader.toHash,
      stateRoot = stateRoot,
      transactionsRoot = crypto.hash(List(txHash)),
      timestamp = Instant.ofEpochMilli(now),
    )
    newBlockHash = crypto.hash(newBlockHeader)
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"next block header: $newBlockHeader")))
    signature <- EitherT.fromEither[F](localKeyPair.sign(newBlockHash.toArray))
    newBlock = Block(
      header = newBlockHeader,
      transactionHashes = Set(txHash),
      votes = Set(signature),
    )
    _ <- implicitly[BlockRepository[F]].put(newBlock)
    _ <- StateRepository.put(state)
    _ <- implicitly[TransactionRepository[F]].put(transaction)
  } yield txHash
}
