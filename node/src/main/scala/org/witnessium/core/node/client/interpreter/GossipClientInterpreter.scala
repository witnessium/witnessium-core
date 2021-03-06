package org.witnessium.core
package node
package client
package interpreter

import cats.data.OptionT
import cats.implicits._
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Method, Request, Response}
import com.twitter.util.Future
import io.circe.generic.auto._
import io.circe.parser
import io.circe.refined._
import io.circe.syntax._

import codec.circe._
import datatype.UInt256Bytes
import model.{Block, GossipMessage, NodeStatus, Transaction}
import p2p.BloomFilter

class GossipClientInterpreter(hostname: String, port: Port) extends GossipClient[Future] {
  private val client: Service[Request, Response] = Http.client.newService(s"$hostname:$port")

  def status: Future[Either[String, NodeStatus]] = {
    val request = Request(Method.Get, ApiPath.gossip.status.toUrl)

    scribe.info(s"Gossip status request: $request")

    for (response <- client(request)) yield {
      scribe.info(s"Gossip status response: ${response}")
      parser.decode[NodeStatus](response.contentString).left.map(_.getMessage())
    }
  }

  def bloomfilter(bloomfilter: BloomFilter): Future[Either[String, GossipMessage]] = {
    val request = Request(Method.Post, ApiPath.gossip.bloomfilter.toUrl)
    request.setContentString(bloomfilter.asJson.toString)
    request.setContentTypeJson()
    scribe.info(s"Gossip bloomfilter request: $request")

    for (response <- client(request)) yield {
      scribe.info(s"Gossip bloomfilter response: $response")
      scribe.info(s"Gossip bloomfilter response body: ${response.contentString}")
      parser.decode[GossipMessage](response.contentString).left.map{ _ =>
        s"$response: ${response.contentString}"
      }
    }
  }

  def unknownTransactions(transactionHashes: Seq[UInt256Bytes]): Future[Either[String, Seq[Transaction.Verifiable]]] = {
    val request = Request(Method.Post, ApiPath.gossip.unknownTransactions.toUrl)
    request.setContentString(transactionHashes.asJson.toString)
    request.setContentTypeJson()
    scribe.info(s"Gossip unknownTransactions request: $request")

    for (response <- client(request)) yield {
      scribe.info(s"Gossip unknownTransactions response: $response")
      scribe.debug(s"Gossip unknownTransactions response body: ${response.contentString}")
      parser.decode[Seq[Transaction.Verifiable]](response.contentString).left.map{ _ =>
        s"$response: ${response.contentString}"
      }
    }
  }

  def block(blockHash: UInt256Bytes): Future[Either[String, Option[Block]]] = {
    val request = Request(s"${ApiPath.gossip.block.toUrl}/${blockHash.toHex}")
    scribe.info(s"Gossip block request: $request")

    for (response <- client(request)) yield {
      scribe.info(s"Gossip block response: $response")
      scribe.debug(s"Gossip block response body: ${response.contentString}")
      if (response.statusCode === 404) Right(None) else OptionT.liftF{
        parser.decode[Block](response.contentString).left.map{ _ =>
          s"$response: ${response.contentString}"
        }
      }.value
    }
  }

  def close(): Future[Unit] = client.close()
}
