package org.witnessium.core
package node

import cats.effect.IO
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.filter.Cors
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.param.Stats
import com.twitter.io.Buf
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import eu.timepit.refined.pureconfig._
import io.circe.generic.auto._
import io.circe.refined._
import io.finch._
import io.finch.catsEffect._
import io.finch.circe._
import pureconfig.{CamelCase, ConfigFieldMapping, SnakeCase}
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.auto._
import pureconfig.generic.ProductHint

import codec.circe._
import datatype.Confidential
import endpoint.{GossipEndpoint, JsFileEndpoint, TransactionEndpoint}
import model.NetworkId
import service.{GossipService, TransactionService}
import service.interpreter.GossipServiceInterpreter
import util.ServingHtml
import view.Index

object WitnessiumNode extends TwitterServer with ServingHtml {

  /****************************************
   *  Load Config
   ****************************************/

  final case class NodeConfig(networkId: NetworkId, port: Port, privateKey: Confidential[String])
  final case class PeerConfig(hostname: String, port: Int, publicKey: String)
  final case class Config(node: NodeConfig, peers: List[PeerConfig])

  implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, SnakeCase))

  @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
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

  val gossipService: GossipService[IO] = new GossipServiceInterpreter()
  val transactionService: TransactionService[IO] = new TransactionService[IO]

  /****************************************
   *  Setup Endpoints and API
   ****************************************/

  val gossipEndpoint: GossipEndpoint = new GossipEndpoint(gossipService)
  val transactionEndpoint: TransactionEndpoint = new TransactionEndpoint(transactionService)

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  val htmlEndpoint: Endpoint[IO, Html] = get(pathEmpty) { Ok(Index.skeleton) }

  val javascriptEndpoint: Endpoint[IO, Buf] = new JsFileEndpoint().Get

  val jsonEndpoint = (transactionEndpoint.Post
    :+: gossipEndpoint.Status
    :+: gossipEndpoint.BloomFilter
    :+: gossipEndpoint.UnknownTransactions
    :+: gossipEndpoint.State
    :+: gossipEndpoint.Block
  )

  val policy: Cors.Policy = Cors.Policy(
    allowsOrigin = _ => Some("*"),
    allowsMethods = _ => Some(Seq("GET", "POST")),
    allowsHeaders = _ => Some(Seq("Accept"))
  )

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  lazy val api: Service[Request, Response] = new Cors.HttpFilter(policy).andThen(Bootstrap
    .serve[Text.Html](htmlEndpoint)
    .serve[Application.Javascript](javascriptEndpoint)
    .serve[Application.Json](jsonEndpoint)
    .toService
  )

  scribe.info(s"=== Endpoints ===")
  scribe.info(s"HTML: $htmlEndpoint")
  scribe.info(s"Javascript: $javascriptEndpoint")
  scribe.info(s"JSON: $jsonEndpoint")

  /****************************************
   *  Run Server
   ****************************************/
  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def main(): Unit = {
    try {
      val server = Http.server
        .withStreaming(enabled = true)
        .configured(Stats(statsReceiver))
        .serve(s":${nodeConfig.port}", api)

      onExit {
        { val _ = adminHttpServer.close() }
        { val _ = server.close() }
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
