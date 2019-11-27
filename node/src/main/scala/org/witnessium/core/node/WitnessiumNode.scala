package org.witnessium.core
package node

import java.nio.file.{Path, Paths}
import java.time.Instant
import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.twitter.finagle.{Http, ListeningServer, Service}
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
import swaydb.{IO => SwayIO}
import swaydb.serializers.Default.ArraySerializer

import codec.circe._
import client.GossipClient
import client.interpreter.GossipClientInterpreter
import crypto.Hash.ops._
import datatype.{BigNat, Confidential, MerkleTrieNode, UInt256Bytes}
import endpoint._
import model.{Address, Block, BlockHeader, NetworkId, Transaction}
import repository._
import repository.StateRepository._
import service._
import store.{HashStore, KeyValueStore, SingleValueStore, StoreIndex}
import store.interpreter._
import util.{EncodeException, ServingHtml}
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

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val (genesisBlock, genesisState, genesisTransaction) = GenesisBlockSetupService.getGenesisBlock(
    networkId = nodeConfig.networkId,
    genesisInstant = genesisConfig.createdAt,
    initialDistribution = genesisConfig.initialDistribution,
  )

  scribe.info(s"genesis state: $genesisState")

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: cats.effect.Timer[IO] = IO.timer(ec)

  /****************************************
   *  Setup Repositories
   ****************************************/
  def swayDb(dir: Path): swaydb.Map[Array[Byte], Array[Byte], Nothing, SwayIO.ApiIO] =
    swaydb.persistent.Map[Array[Byte], Array[Byte], Nothing, SwayIO.ApiIO](dir).get
  def swayInmemoryDb: swaydb.Map[Array[Byte], Array[Byte], Nothing, SwayIO.ApiIO] =
    swaydb.memory.Map[Array[Byte], Array[Byte], Nothing, SwayIO.ApiIO]().get

  implicit val bestBlockHeaderStore: SingleValueStore[IO, BlockHeader] =
    new SingleValueStoreSwayInterpreter[BlockHeader](swayDb(Paths.get("sway","block", "best-header")))

  implicit val blockHashStore: HashStore[IO, Block] = new HashStoreSwayInterpreter[Block](
    swayDb(Paths.get("sway", "block"))
  )

  implicit val blockNumberStoreIndex: StoreIndex[IO, BigNat, UInt256Bytes] =
    StoreIndexSwayInterpreter.reverseBignatStoreIndex(Paths.get("sway", "block", "index", "block-number"))

  implicit val stateHashStore: HashStore[IO, MerkleTrieNode] = new HashStoreSwayInterpreter[MerkleTrieNode](
    swayDb(Paths.get("sway", "state"))
  )

  implicit val txBlockIndex: KeyValueStore[IO, UInt256Bytes, UInt256Bytes] =
    new StoreIndexSwayInterpreter(Paths.get("sway", "block", "index", "txhash"))

  val blockRepository: BlockRepository[IO] = implicitly[BlockRepository[IO]]

  implicit val transctionHashStore: HashStore[IO, Transaction.Verifiable] =
    new HashStoreSwayInterpreter[Transaction.Verifiable](swayDb(Paths.get("sway", "transaction")))

  implicit val addressTransactionIndex: StoreIndex[IO, (Address, UInt256Bytes), Unit] =
    new StoreIndexSwayInterpreter(Paths.get("sway", "transaction", "index", "address"))

  val transactionRepository: TransactionRepository[IO] = implicitly[TransactionRepository[IO]]

  /****************************************
   *  Setup Clients
   ****************************************/

  val clients: List[GossipClient[TwitterFuture]] = peersConfig.map { peerConfig =>
    new GossipClientInterpreter(peerConfig.hostname, peerConfig.port)
  }

  /****************************************
   *  Setup Endpoints and API
   ****************************************/

  val addressEndpoint: AddressEndpoint = new AddressEndpoint()
  val nodeStatusEndpoint: NodeStatusEndpoint = new NodeStatusEndpoint(nodeConfig.networkId, genesisBlock.toHash)
  val blockEndpoint: BlockEndpoint = new BlockEndpoint()
  val transactionEndpoint: TransactionEndpoint = new TransactionEndpoint(localKeyPair)

  val htmlEndpoint: Endpoint[IO, Html] = get(pathEmpty) { Ok(Index.skeleton) }

  val javascriptEndpoint: Endpoint[IO, Buf] = new JsFileEndpoint().Get

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val jsonEndpoint = (nodeStatusEndpoint.Get
    :+: addressEndpoint.Get
    :+: blockEndpoint.Index
    :+: blockEndpoint.Get
    :+: blockEndpoint.GetInfo
    :+: transactionEndpoint.Index
    :+: transactionEndpoint.Get
    :+: transactionEndpoint.Post
    :+: transactionEndpoint.GetInfo
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
  val startIO:  EitherT[IO, String, Unit] = for {
    _ <-  NodeInitializationService.initialize[IO](
      genesisBlock = genesisBlock,
      genesisState = genesisState,
      genesisTransaction = genesisTransaction
    )
  } yield ()

  startIO.value.unsafeToFuture().onComplete {
    case scala.util.Success(v) =>
      scribe.info(s"startIO finishes successfully: $v")
    case scala.util.Failure(t) =>
      scribe.error("An error has occurred in startIO: " + t.getMessage)
      t.printStackTrace()
  }

  val server: ListeningServer = Http.server
    .withStreaming(enabled = true)
    .configured(Stats(statsReceiver))
    .serve(s":${nodeConfig.port}", api)

  onExit {
    { val _ = adminHttpServer.close() }
    { val _ = server.close() }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def main(): Unit = {
    try {
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
