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
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.auto._
import pureconfig.generic.ProductHint

import endpoint.JsFileEndpoint
import util.ServingHtml
import view.Index

object WitnessiumNode extends TwitterServer with ServingHtml {

  /****************************************
   *  Load Config
   ****************************************/

  final case class NodeConfig(port: Int, privateKey: String)
  final case class PeerConfig(hostname: String, port: Int, publicKey: String)
  final case class Config(node: NodeConfig, peers: List[PeerConfig])

  implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, SnakeCase))

  val configEither: Either[ConfigReaderFailures, Config] = pureconfig.loadConfig[Config]
  scribe.info(s"load config: $configEither")

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val Right(Config(nodeConfig, peersConfig)) = configEither

  /****************************************
   *  Setup Algebra Interpreters
   ****************************************/

  /****************************************
   *  Setup Services
   ****************************************/

  /****************************************
   *  Setup Endpoints and API
   ****************************************/
  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  val index: Endpoint[IO, Html] = get(pathEmpty) { Ok(Index.skeleton) }

  val jsFileEndpoint: JsFileEndpoint = new JsFileEndpoint()

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  lazy val api: Service[Request, Response] = Bootstrap
    .serve[Text.Html](index)
    .serve[Application.Javascript](jsFileEndpoint())
    .toService

  /****************************************
   *  Run Server
   ****************************************/
  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def main(): Unit = {
    try {
      val server = Http.server.serve(s":${nodeConfig.port}", api)
      onExit {
        val _ = server.close()
      }
      val _ = Await.ready(server)
    } catch {
      case _: java.lang.InterruptedException =>
        scribe.info("Server execution is interrupted.")
      case e: Exception =>
        scribe.error(e)
        e.printStackTrace()
    }
  }
}
