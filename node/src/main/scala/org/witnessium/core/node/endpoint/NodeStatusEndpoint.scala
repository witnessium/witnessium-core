package org.witnessium.core
package node
package endpoint

import cats.effect.IO
import io.finch._
import io.finch.catsEffect._

import datatype.UInt256Bytes
import model.{NetworkId, NodeStatus}
import repository.BlockRepository
import service.LocalStatusService

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class NodeStatusEndpoint(
  networkId: NetworkId,
  genesisHash: UInt256Bytes,
)(implicit blockRepository: BlockRepository[IO]) {

  val Get: Endpoint[IO, NodeStatus] = get("status") {
    scribe.info("status request")
    LocalStatusService.status[IO](networkId, genesisHash).value.map{
      case Left(msg) =>
        scribe.info(s"status response bad request: $msg")
        BadRequest(new Exception(msg))
      case Right(status) =>
        scribe.info(s"status response: $status")
        Ok(status)
    }
  }
}
