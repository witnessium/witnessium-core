package org.witnessium.core
package node
package endpoint

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.refined._
import io.finch._
import io.finch.catsEffect._
import io.finch.circe._

import codec.circe._
import model.{GossipMessage, NodeStatus}
import p2p.BloomFilter
import service.GossipService

class GossipEndpoint(gossipService: GossipService[IO]) {
  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  val Status: Endpoint[IO, NodeStatus] = get(ApiPath.gossip.status.toEndpoint) {
    gossipService.status.map {
      case Right(status) => Ok(status)
      case Left(errorMsg) => InternalServerError(new Exception(errorMsg))
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  val BloomFilter: Endpoint[IO, GossipMessage] = post(ApiPath.gossip.bloomfilter.toEndpoint ::
    jsonBody[BloomFilter]
  ) { (bloomfilter: BloomFilter) =>
    scribe.info(s"Receive gossip bloomfilter request: $bloomfilter")
    gossipService.bloomfilter(bloomfilter).map {
      case Right(message) => Ok(message)
      case Left(errorMsg) =>
        scribe.info(s"Sending gossip bloomfilter error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }
}
