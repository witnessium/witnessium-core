package org.witnessium.core
package node
package endpoint

import cats.effect.IO
import io.finch._
import io.finch.catsEffect._

import model.NodeStatus
import service.GossipService

class GossipEndpoint(gossipService: GossipService[IO]) {
  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  val Status: Endpoint[IO, NodeStatus] = get(ApiPath.gossip.status.toEndpoint) {
    gossipService.status.map {
      case Right(status) => Ok(status)
      case Left(errorMsg) => InternalServerError(new Exception(errorMsg))
    }
  }
}
