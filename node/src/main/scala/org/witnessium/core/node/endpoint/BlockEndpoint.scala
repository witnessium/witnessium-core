package org.witnessium.core
package node
package endpoint

import cats.effect.Async
import cats.implicits._
import io.finch._
import io.finch.refined._

import datatype.{BigNat, UInt256Bytes}
import model.Block
import model.api.{BlockInfo, BlockInfoBrief}
import repository.{BlockRepository, TransactionRepository}
import service.BlockService

object BlockEndpoint {

  def Index[F[_]: Async: BlockRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, List[BlockInfoBrief]] = {

    import finch._

    get("block"
      :: paramOption[BigNat]("from")
      :: paramOption[Int]("limit")
    ) { (fromOption: Option[BigNat], limitOption: Option[Int]) =>
      BlockService.list[F](fromOption, limitOption getOrElse 15).value.map {
        case Right(blocks) => Ok(blocks)
        case Left(errorMsg) =>
          scribe.info(s"Index block with from:$fromOption limit:limitOption error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }

  def Get[F[_]: Async: BlockRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, Block] = {

    import finch._

    get("block" :: path[UInt256Bytes].withToString("{blockHash}")) { (blockHash: UInt256Bytes) =>
      BlockService.get[F](blockHash).value.map {
        case Right(Some(block)) => Ok(block)
        case Right(None) => NotFound(new Exception(s"Not found: $blockHash"))
        case Left(errorMsg) =>
          scribe.info(s"Get block $blockHash error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }

  def GetInfo[F[_]: Async: BlockRepository: TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, BlockInfo] = {

    import finch._

    get("blockinfo" :: path[BigNat].withToString("number")) { (number: BigNat) =>
      BlockService.findByBlockNumber[F](number).value.map {
        case Right(Some(blockInfo)) => Ok(blockInfo)
        case Right(None) => NotFound(new Exception(s"Not found: $number"))
        case Left(errorMsg) =>
          scribe.info(s"Get block by number $number error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }
}
