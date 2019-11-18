package org.witnessium.core
package node
package repository

import cats.Monad
import cats.data.EitherT
import datatype.UInt256Bytes
import model.{Block, BlockHeader}
import store.{HashStore, SingleValueStore}

trait BlockRepository[F[_]] {
  def bestHeader: EitherT[F, String, Option[BlockHeader]]
  def get(hash: UInt256Bytes): EitherT[F, String, Option[Block]]
  def put(block: Block): EitherT[F, String, Unit]
}

object BlockRepository {

  implicit def fromStores[F[_]: Monad](implicit
    bestBlockHeaderStore: SingleValueStore[F, BlockHeader],
    blockHashStore: HashStore[F, Block]
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
          bestBlockHeaderStore.put(block.header)
      })
    } yield ()
  }
}
