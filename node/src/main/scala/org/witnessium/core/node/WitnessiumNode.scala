package org.witnessium.core
package node

import cats.effect.IO
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import io.finch._
import io.finch.catsEffect._
//import io.finch.circe._
//import io.circe.generic.auto._
import pureconfig.{CamelCase, ConfigFieldMapping, SnakeCase}
import pureconfig.generic.auto._
import pureconfig.generic.ProductHint

import endpoint.JsFileEndpoint
import util.ServingHtml
import view.Index

object WitnessiumNode extends TwitterServer with ServingHtml {

  /****************************************
   *  Load Config
   ****************************************/

  case class NodeConfig(port: Int, privateKey: String)
  case class PeerConfig(hostname: String, port: Int, publicKey: String)
  case class Config(node: NodeConfig, peers: List[PeerConfig])

  implicit def hint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, SnakeCase))

  val configEither = pureconfig.loadConfig[Config]
  scribe.info(s"load config: $configEither")

  val Config(nodeConfig, peersConfig) = configEither.right.get

  /****************************************
   *  Setup Algebra Interpreters
   ****************************************/

  /****************************************
   *  Setup Services
   ****************************************/

  /****************************************
   *  Setup Endpoints and API
   ****************************************/
  val index: Endpoint[IO, Html] = get(pathEmpty) { Ok(Index.skeleton) }

  val jsFileEndpoint: JsFileEndpoint = new JsFileEndpoint()

  lazy val api: Service[Request, Response] = Bootstrap
    .serve[Text.Html](index)
    .serve[Application.Javascript](jsFileEndpoint())
    .toService

  /****************************************
   *  Run Server
   ****************************************/
  def main(): Unit = {
    try {
      val server = Http.server.serve(":8081", api)
      onExit {
        server.close()
        ()
      }
      Await.ready(server)
      ()
    } catch {
      case _: java.lang.InterruptedException =>
        scribe.info("Server execution is interrupted.")
      case e: Exception =>
        scribe.error(e)
        e.printStackTrace()
    }
  }
}
