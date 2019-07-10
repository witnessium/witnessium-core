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

import codec.circe._
import model.NodeStatus

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

  def close(): Future[Unit] = client.close()
}
