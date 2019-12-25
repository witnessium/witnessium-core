package org.witnessium.core
package node
package endpoint

import cats.effect.Async
import cats.implicits._
import io.finch._

import datatype.UInt256Bytes
import model.{NetworkId, NodeStatus}
import repository.BlockRepository
import service.LocalStatusService

object NodeStatusEndpoint{

  def Get[F[_]: Async: BlockRepository](
    networkId: NetworkId,
    genesisHash: UInt256Bytes,
  )(implicit finch: EndpointModule[F]): Endpoint[F, NodeStatus] = {

    import finch._

    get("status") {
      scribe.info("status request")
      LocalStatusService.status[F](networkId, genesisHash).value.map{
        case Left(msg) =>
          scribe.info(s"status response bad request: $msg")
          BadRequest(new Exception(msg))
        case Right(status) =>
          scribe.info(s"status response: $status")
          Ok(status)
      }
    }
  }
}
