package org.witnessium.core
package node
package endpoint

import cats.effect.IO
import io.finch._
import io.finch.catsEffect._

import datatype.UInt256Bytes
import model.Block
import repository.BlockRepository

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class BlockEndpoint()(implicit blockRepository: BlockRepository[IO]) {

  val Get: Endpoint[IO, Block] = get("block" ::
    path[UInt256Bytes].withToString("{blockHash}")
  ) { (blockHash: UInt256Bytes) =>
    blockRepository.get(blockHash).value.map {
      case Right(Some(block)) => Ok(block)
      case Right(None) => NotFound(new Exception(s"Not found: $blockHash"))
      case Left(errorMsg) =>
        scribe.info(s"Get block $blockHash error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }
}
