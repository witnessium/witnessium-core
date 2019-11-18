package org.witnessium.core
package node
package service

import java.time.Instant
import scala.concurrent.duration._
import cats.data.EitherT
import cats.effect.{Sync, Timer}
import eu.timepit.refined.refineV
import eu.timepit.refined.numeric.NonNegative

import crypto.KeyPair
import crypto.Hash.ops._
import datatype.UInt256Bytes
import model.{Block, BlockHeader, Transaction}
import repository.{BlockRepository, TransactionRepository}

object TransactionService {

  def submit[F[_]: Timer: Sync: BlockRepository: TransactionRepository](
    transaction: Transaction.Signed,
    localKeyPair: KeyPair,
  ): EitherT[F, String, UInt256Bytes] = for {
    bestBlockHeaderOption <- implicitly[BlockRepository[F]].bestHeader
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"Best block header: $bestBlockHeaderOption")))
    bestBlockHeader <- EitherT.fromOption[F](bestBlockHeaderOption, "No best block header")
    txHash = transaction.toHash
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"generating new block with transactions: $txHash")))
    number <- EitherT.fromEither[F](refineV[NonNegative](bestBlockHeader.number.value + 1))
    now <- EitherT.right[String](Timer[F].clock.realTime(MILLISECONDS))
    newBlockHeader = BlockHeader(
      number = number,
      parentHash = bestBlockHeader.toHash,
      stateRoot = crypto.hash(datatype.UInt256Refine.EmptyBytes),
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
    _ <- implicitly[TransactionRepository[F]].put(transaction)
  } yield txHash
}
