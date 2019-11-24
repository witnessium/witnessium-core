package org.witnessium.core
package node
package endpoint

import cats.effect.IO
import io.finch._
import io.finch.catsEffect._
import io.finch.refined._

import datatype.{BigNat, UInt256Bytes}
import model.Block
import model.api.BlockInfoBrief
import repository.BlockRepository
import service.BlockService

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class BlockEndpoint()(implicit blockRepository: BlockRepository[IO]) {

  val Index: Endpoint[IO, List[BlockInfoBrief]] = get("block" ::
    paramOption[BigNat]("from") ::
    paramOption[Int]("limit")
  ) { (fromOption: Option[BigNat], limitOption: Option[Int]) =>
    BlockService.list[IO](fromOption, limitOption getOrElse 15).value.map {
      case Right(blocks) => Ok(blocks)
      case Left(errorMsg) =>
        scribe.info(s"Index block with from:$fromOption limit:limitOption error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }

  val Get: Endpoint[IO, Block] = get("block" ::
    path[UInt256Bytes].withToString("{blockHash}")
  ) { (blockHash: UInt256Bytes) =>
    BlockService.get(blockHash).value.map {
      case Right(Some(block)) => Ok(block)
      case Right(None) => NotFound(new Exception(s"Not found: $blockHash"))
      case Left(errorMsg) =>
        scribe.info(s"Get block $blockHash error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }
}
