package org.witnessium.core
package node

import java.nio.file.{Path, Paths}
import java.time.Instant
import cats.effect.IO
import cats.effect.concurrent.Ref
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.filter.Cors
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.param.Stats
import com.twitter.io.Buf
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future => TwitterFuture}
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
import swaydb.data.{IO => SwayIO}
import swaydb.serializers.Default.ArraySerializer

import codec.circe._
import client.GossipClient
import client.interpreter.GossipClientInterpreter
import datatype.{BigNat, Confidential, UInt256Bytes, UInt256Refine}
import endpoint.{BlockEndpoint, GossipEndpoint, JsFileEndpoint, NodeStatusEndpoint, TransactionEndpoint}
import model.{Address, NetworkId}
import repository._
import repository.interpreter._
import service._
import service.interpreter._
import util.{EncodeException, ServingHtml}
import util.SwayIOCats._
import view.Index

object WitnessiumNode extends TwitterServer with ServingHtml with EncodeException {

  /****************************************
   *  Load Config
   ****************************************/

  final case class NodeConfig(networkId: NetworkId, port: Port, nodeNumber: Int, privateKey: Confidential[String])
  final case class PeerConfig(hostname: String, port: Port, nodeNumber: Int, publicKey: String)
  final case class GenesisConfig(initialDistribution: Map[Address, BigNat], createdAt: Instant)
  final case class Config(node: NodeConfig, peers: List[PeerConfig], genesis: GenesisConfig)

  implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, SnakeCase))

  val configEither: Either[ConfigReaderFailures, Config] = pureconfig.loadConfig[Config]
  scribe.info(s"load config: $configEither")

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val Right(Config(nodeConfig, peersConfig, genesisConfig)) = configEither

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val Right(localKeyPair) = for {
    bytes <- scodec.bits.ByteVector.fromBase64Descriptive(nodeConfig.privateKey.value)
  } yield crypto.KeyPair.fromPrivate(BigInt(1, bytes.toArray))

  /****************************************
   *  Setup Repositories
   ****************************************/
  def swayDb(dir: Path): swaydb.Map[Array[Byte], Array[Byte], SwayIO] = swaydb.persistent.Map(dir).get
  def swayInmemoryDb: swaydb.Map[Array[Byte], Array[Byte], SwayIO] = swaydb.memory.Map().get

  val blockRepository: BlockRepository[SwayIO] = new BlockRepositoryInterpreter(
    swayBestHeaderMap = swayDb(Paths.get("sway", "block", "best-header")),
    swayHeaderMap = swayDb(Paths.get("sway", "block", "header")),
    swayTransactionsMap = swayDb(Paths.get("sway", "block", "transaction")),
    swaySignaturesMap = swayDb(Paths.get("sway", "block", "signature")),
  )

  val gossipRepository: GossipRepository[SwayIO] = new GossipRepositoryInterpreter(
    genesisHashRef = Ref.unsafe[SwayIO, UInt256Bytes](UInt256Refine.EmptyBytes),
    swayBlockSuggestionMap = swayInmemoryDb,
    swayBlockVoteMap = swayInmemoryDb,
    swayNewTransactionMap = swayInmemoryDb,
  )

  val stateRepository: StateRepository[SwayIO] = new StateRepositoryInterpreter(
    swayDb(Paths.get("sway", "state"))
  )

  val transactionRepository: TransactionRepository[SwayIO] = new TransactionRepositoryInterpreter(
    swayDb(Paths.get("sway", "transaction"))
  )

  /****************************************
   *  Setup Clients
   ****************************************/

  val clients: List[GossipClient[TwitterFuture]] = peersConfig.map { peerConfig =>
    new GossipClientInterpreter(peerConfig.hostname, peerConfig.port)
  }

  /****************************************
   *  Setup Services
   ****************************************/
  val nodeStateUpdateService: NodeStateUpdateService[IO] = new NodeStateUpdateServiceInterpreter(
    blockRepository, gossipRepository
  )

  val blockExplorerService: BlockExplorerService[IO] = new BlockExplorerServiceInterpreter(
    blockRepository, gossipRepository, stateRepository, transactionRepository
  )

  val blockSuggestionService: BlockSuggestionService[IO] = new BlockSuggestionServiceInterpreter(
    localKeyPair = localKeyPair,
    gossipListener = nodeStateUpdateService.onGossip,
    blockRepository = blockRepository,
    gossipRepository = gossipRepository
  )

  val localGossipService: LocalGossipService[IO] =
    new LocalGossipServiceInterpreter(nodeConfig.networkId, blockRepository, gossipRepository)

  val transactionService: TransactionService[IO] = new TransactionServiceInterpreter(
    nodeStateUpdateService.onGossip
  )

  val peerConnectionService: PeerConnectionService[TwitterFuture] = new PeerConnectionServiceInterpreter(clients)

  val genesisBlockSetupService: GenesisBlockSetupService[SwayIO] = new GenesisBlockSetupServiceInterpreter(
    networkId = nodeConfig.networkId,
    genesisInstant = genesisConfig.createdAt,
    initialDistribution = genesisConfig.initialDistribution,
    blockRepository = blockRepository,
    stateRepository = stateRepository,
    transactionRepository = transactionRepository,
    gossipRepository = gossipRepository,
  )

  val nodeInitializationService: NodeInitializationService[IO] = new NodeInitializationServiceInterpreter(
    genesisBlockSetupService = genesisBlockSetupService,
    localGossipService = localGossipService,
    peerConnectionService = peerConnectionService,
    stateRepository = stateRepository,
    transactionRepository = transactionRepository,
    blockRepository = blockRepository,
  )

  /****************************************
   *  Setup Endpoints and API
   ****************************************/

  val gossipEndpoint: GossipEndpoint = new GossipEndpoint(localGossipService)
  val nodeStatusEndpoint: NodeStatusEndpoint = new NodeStatusEndpoint(localGossipService)
  val blockEndpoint: BlockEndpoint = new BlockEndpoint(blockExplorerService)
  val transactionEndpoint: TransactionEndpoint = new TransactionEndpoint(transactionService, blockExplorerService)

  val htmlEndpoint: Endpoint[IO, Html] = get(pathEmpty) { Ok(Index.skeleton) }

  val javascriptEndpoint: Endpoint[IO, Buf] = new JsFileEndpoint().Get

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val jsonEndpoint = (nodeStatusEndpoint.Get
    :+: blockEndpoint.Get
    :+: transactionEndpoint.Get
    :+: transactionEndpoint.Post
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

    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

    import cats.implicits._
    implicit val timer: cats.effect.Timer[IO] = IO.timer(ec)

    //val startIO: IO[Either[String, Unit]] = genesisBlockSetupService()
    val startIO: IO[Unit] = nodeInitializationService.initialize *> blockSuggestionService.run

    startIO.unsafeToFuture().onComplete {
      case scala.util.Success(v) =>
        scribe.info(s"startIO finishes successfully: $v")
      case scala.util.Failure(t) =>
        scribe.error("An error has occurred in startIO: " + t.getMessage)
        t.printStackTrace()
    }

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
