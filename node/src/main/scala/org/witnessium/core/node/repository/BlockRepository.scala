package org.witnessium.core
package node
package repository

import cats.Monad
import cats.data.EitherT
import cats.implicits._

import crypto.Hash.ops._
import datatype.{BigNat, UInt256Bytes}
import model.{Block, BlockHeader}
import store.{HashStore, KeyValueStore, SingleValueStore, StoreIndex}

trait BlockRepository[F[_]] {
  def bestHeader: EitherT[F, String, Option[BlockHeader]]
  def get(hash: UInt256Bytes): EitherT[F, String, Option[Block]]
  def put(block: Block): EitherT[F, String, Unit]

  def listFrom(blockNumber: BigNat, limit: Int): EitherT[F, String, List[(BigNat, UInt256Bytes)]]
  def findByTransaction(txHash: UInt256Bytes): EitherT[F, String, Option[UInt256Bytes]]
}

object BlockRepository {

  implicit def fromStores[F[_]: Monad](implicit
    bestBlockHeaderStore: SingleValueStore[F, BlockHeader],
    blockHashStore: HashStore[F, Block],
    blockNumberIndex: StoreIndex[F, BigNat, UInt256Bytes],
    txBlockIndex: KeyValueStore[F, UInt256Bytes, UInt256Bytes],
  ): BlockRepository[F] = new BlockRepository[F] {

    def bestHeader: EitherT[F, String, Option[BlockHeader]] = bestBlockHeaderStore.get

    def get(blockHash: UInt256Bytes): EitherT[F,String,Option[Block]] = blockHashStore.get(blockHash)

    def put(block: Block): EitherT[F,String,Unit] = for {
      _ <- blockHashStore.put(block)
      bestHeaderOption <- bestHeader
      _ <- (bestHeaderOption match {
        case Some(best) if best.number.value >=  block.header.number.value =>
          EitherT.pure[F, String](())
        case _ =>
          val blockHash = block.toHash
          bestBlockHeaderStore.put(block.header) *> EitherT.right[String](for {
            _ <- blockNumberIndex.put(block.header.number, block.toHash)
            _ <- block.transactionHashes.toList.traverse{ txHash => txBlockIndex.put(txHash, blockHash) }
          } yield ())
      })
    } yield ()

    def listFrom(blockNumber: BigNat, limit: Int): EitherT[F, String, List[(BigNat, UInt256Bytes)]] =
      blockNumberIndex.from(blockNumber, Some(limit))

    def findByTransaction(txHash: UInt256Bytes): EitherT[F, String, Option[UInt256Bytes]] = txBlockIndex.get(txHash)
  }
}
