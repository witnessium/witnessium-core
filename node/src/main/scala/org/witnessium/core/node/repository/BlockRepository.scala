package org.witnessium.core
package node
package repository

import cats.Monad
import cats.data.EitherT
import datatype.UInt256Bytes
import model.{Block, BlockHeader}
import store.{HashStore, SingleValueStore}

trait BlockRepository[F[_]] {
  def bestHeader: EitherT[F, String, BlockHeader]
  def get(hash: UInt256Bytes): EitherT[F, String, Option[Block]]
  def put(block: Block): EitherT[F, String, Unit]
}

object BlockRepository {

  implicit def fromStores[F[_]: Monad](implicit
    bestBlockHeaderStore: SingleValueStore[F, BlockHeader],
    blockHashStore: HashStore[F, Block]
  ): BlockRepository[F] = new BlockRepository[F] {

    def bestHeader: EitherT[F, String, BlockHeader] = for {
      headerOption <- bestBlockHeaderStore.get
      header <- EitherT.fromOption[F](headerOption, "Do not exist best block header")
    } yield header

    def get(blockHash: UInt256Bytes): EitherT[F,String,Option[Block]] = blockHashStore.get(blockHash)

    def put(block: Block): EitherT[F,String,Unit] = for {
      _ <- blockHashStore.put(block)
      bestHeaderOption <- bestBlockHeaderStore.get
      _ <- (bestHeaderOption match {
        case Some(best) if best.number.value <=  block.header.number.value =>
          bestBlockHeaderStore.put(block.header)
        case _ => EitherT.pure[F, String](())
      })
    } yield ()
  }
}
