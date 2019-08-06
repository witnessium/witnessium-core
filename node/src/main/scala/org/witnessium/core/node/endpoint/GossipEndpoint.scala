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
import service.LocalGossipService

class GossipEndpoint(localGossipService: LocalGossipService[IO]) {
  val Status: Endpoint[IO, NodeStatus] = get(ApiPath.gossip.status.toEndpoint) {
    localGossipService.status.map {
      case Right(status) => Ok(status)
      case Left(errorMsg) => InternalServerError(new Exception(errorMsg))
    }
  }

  val BloomFilter: Endpoint[IO, GossipMessage] = post(ApiPath.gossip.bloomfilter.toEndpoint ::
    jsonBody[BloomFilter]
  ) { (bloomfilter: BloomFilter) =>
    scribe.info(s"Receive gossip bloomfilter request: $bloomfilter")
    localGossipService.bloomfilter(bloomfilter).map {
      case Right(message) => Ok(message)
      case Left(errorMsg) =>
        scribe.info(s"Sending gossip bloomfilter error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }

  val UnknownTransactions: Endpoint[IO, Seq[Transaction.Verifiable]] = post(
    ApiPath.gossip.unknownTransactions.toEndpoint :: jsonBody[List[UInt256Bytes]]
  ) { (transactionHashes: List[UInt256Bytes]) =>
    scribe.info(s"Receive gossip unknown transactions request: $transactionHashes")
    localGossipService.unknownTransactions(transactionHashes).map {
      case Right(transactions) => Ok(transactions)
      case Left(errorMsg) =>
        scribe.info(s"Sending gossip unknown transactions error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }

  val State: Endpoint[IO, State] = get(
    ApiPath.gossip.state.toEndpoint :: path[UInt256Bytes].withToString("{stateRoot}")
  ){ (stateRoot: UInt256Bytes) =>
    scribe.info(s"Receive gossip state request: $stateRoot")
    localGossipService.state(stateRoot).map {
      case Right(Some(state)) => Ok(state)
      case Right(None) => NotFound(new Exception(s"Not found: $stateRoot"))
      case Left(errorMsg) =>
        scribe.info(s"Sending gossip state error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }

  val Block: Endpoint[IO, Block] = get(
    ApiPath.gossip.block.toEndpoint :: path[UInt256Bytes].withToString("{blockHash}")
  ){ (blockHash: UInt256Bytes) =>
    scribe.info(s"Receive gossip block request: $blockHash")
    localGossipService.block(blockHash).map {
      case Right(Some(block)) => Ok(block)
      case Right(None) => NotFound(new Exception(s"Not found: $blockHash"))
      case Left(errorMsg) =>
        scribe.info(s"Sending gossip block error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }
}
