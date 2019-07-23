package org.witnessium.core
package node
package client
package interpreter

import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Method, Request, Response}
import com.twitter.util.Future
import io.circe.generic.auto._
import io.circe.parser
import io.circe.refined._
import io.circe.syntax._

import codec.circe._
import model.{GossipMessage, NodeStatus}
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

  def close(): Future[Unit] = client.close()
}
