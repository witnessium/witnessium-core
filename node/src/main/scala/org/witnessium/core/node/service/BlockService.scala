package org.witnessium.core
package node
package service

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.implicits._
import datatype.{BigNat, UInt256Bytes}
import model.{Block, BlockHeader}
import model.api.{BlockInfo, BlockInfoBrief}
import repository.BlockRepository

object BlockService {

  def bestHeader[F[_]: Monad: BlockRepository]: EitherT[F, String, BlockHeader] = for {
    headerOption <- implicitly[BlockRepository[F]].bestHeader
    header <- EitherT.fromOption[F](headerOption, s"No best header")
  } yield header

  def blockHashToBlockInfo[F[_]: Monad: BlockRepository](blockHash: UInt256Bytes): EitherT[F, String, BlockInfoBrief] = for {
    blockOption <- implicitly[BlockRepository[F]].get(blockHash)
    block <- EitherT.fromOption[F](blockOption, s"Block $blockHash not found")
  } yield BlockInfoBrief(
    blockNumber = block.header.number,
    blockHash = blockHash,
    createdAt = block.header.timestamp,
    numberOfTransaction = block.transactionHashes.size,
  )

  def list[F[_]: Monad: BlockRepository](
    fromOption: Option[BigNat], limit: Int
  ): EitherT[F, String, List[BlockInfoBrief]] = for {
    from <- fromOption.fold(bestHeader[F].map(_.number))(EitherT.rightT[F, String](_))
    blockHashes <- implicitly[BlockRepository[F]].listFrom(from, limit)
    blockInfos <- blockHashes.unzip._2.traverse[EitherT[F, String, *], BlockInfoBrief](blockHashToBlockInfo)
  } yield blockInfos

  def get[F[_]: BlockRepository](blockHash: UInt256Bytes): EitherT[F, String, Option[Block]] =
    implicitly[BlockRepository[F]].get(blockHash)

  def findByBlockNumber[F[_]: Monad: BlockRepository](
    blockNumber: BigNat
  ): EitherT[F, String, Option[BlockInfo]] = (for {
    (blockNumber1, blockHash) <- OptionT{
      implicitly[BlockRepository[F]].listFrom(blockNumber, 1).map(_.find(_._1 === blockNumber))
    }
    block <- OptionT(implicitly[BlockRepository[F]].get(blockHash))
  } yield BlockInfo(
    blockNumber = blockNumber,
    blockHash = blockHash,
    createdAt = block.header.timestamp,
    numberOfTransaction = block.transactionHashes.size,
    stateRoot = block.header.stateRoot,
    parentHash = block.header.parentHash,
  )).value
}
