package org.witnessium.core
package node
package endpoint

import cats.effect.IO
import io.finch._
import io.finch.catsEffect._

import model.NodeStatus
import service.LocalGossipService

class NodeStatusEndpoint(localGossipService: LocalGossipService[IO]) {

  val Get: Endpoint[IO, NodeStatus] = get("status") {
    scribe.info("status request")
    localGossipService.status.map{
      case Left(msg) =>
        scribe.info(s"status response bad request: $msg")
        BadRequest(new Exception(msg))
      case Right(status) =>
        scribe.info(s"status response: $status")
        Ok(status)
    }
  }
}
