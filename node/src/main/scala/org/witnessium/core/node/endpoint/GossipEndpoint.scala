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
import datatype.UInt256Bytes
import model.{Block, GossipMessage, NodeStatus, State, Transaction}
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

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  val UnknownTransactions: Endpoint[IO, Seq[Transaction.Signed]] = post(ApiPath.gossip.unknownTransactions.toEndpoint ::
    jsonBody[List[UInt256Bytes]]
  ) { (transactionHashes: List[UInt256Bytes]) =>
    scribe.info(s"Receive gossip unknown transactions request: $transactionHashes")
    gossipService.unknownTransactions(transactionHashes).map {
      case Right(transactions) => Ok(transactions)
      case Left(errorMsg) =>
        scribe.info(s"Sending gossip unknown transactions error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  val State: Endpoint[IO, State] = get(
    ApiPath.gossip.state.toEndpoint :: path[UInt256Bytes].withToString("stateRoot")
  ){ (stateRoot: UInt256Bytes) =>
    scribe.info(s"Receive gossip state request: $stateRoot")
    gossipService.state(stateRoot).map {
      case Right(state) => Ok(state)
      case Left(errorMsg) =>
        scribe.info(s"Sending gossip state not found response: $errorMsg")
        NotFound(new Exception(errorMsg))
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  val Block: Endpoint[IO, Block] = get(
    ApiPath.gossip.block.toEndpoint :: path[UInt256Bytes].withToString("blockHash")
  ){ (blockHash: UInt256Bytes) =>
    scribe.info(s"Receive gossip block request: $blockHash")
    gossipService.block(blockHash).map {
      case Right(block) => Ok(block)
      case Left(errorMsg) =>
        scribe.info(s"Sending gossip block not found response: $errorMsg")
        NotFound(new Exception(errorMsg))
    }
  }
}