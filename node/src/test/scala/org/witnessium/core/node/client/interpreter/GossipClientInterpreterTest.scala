package org.witnessium.core
package node
package client
package interpreter

import java.net.{InetAddress, InetSocketAddress}
import cats.effect.IO
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.{Http, ListeningServer}
import com.twitter.util.{Await, Future}
import eu.timepit.refined.refineV
import io.circe.generic.auto._
import io.circe.refined._
import io.finch.circe._
import codec.circe._
import datatype.UInt256Bytes
import endpoint._
import model.{Block, GossipMessage, ModelArbitrary, NodeStatus, State, Transaction}
import p2p.BloomFilter
import service.LocalGossipService
import util.EncodeException

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.rng.Seed
import utest._

object GossipClientInterpreterTest extends TestSuite with ModelArbitrary with EncodeException {

  val seed = Seed.random()

  val sampleNodeStatus = arbitraryNodeStatus.arbitrary.pureApply(Gen.Parameters.default, seed)

  val sampleTransactions = Arbitrary(for {
    count <- Gen.choose(1, 10)
    transactions <- Gen.listOfN(count, arbitraryVerifiable[Transaction].arbitrary)
  } yield transactions).arbitrary.pureApply(Gen.Parameters.default, seed)

  val sampleTransactionHashes = sampleTransactions.map{ signedTransaction => crypto.hash(signedTransaction.value) }

  val sampleState = arbitraryState.arbitrary.pureApply(Gen.Parameters.default, seed)

  val sampleStateRoot = crypto.hash(sampleState)

  val sampleBlock = arbitraryBlock.arbitrary.pureApply(Gen.Parameters.default, seed)

  val sampleBlockHash = crypto.hash(sampleBlock.header)

  val endpoint = new GossipEndpoint(new LocalGossipService[IO] {
    def status: IO[Either[String, NodeStatus]] = {
      IO.pure(Right(sampleNodeStatus))
    }

    def bloomfilter(bloomfilter: BloomFilter): IO[Either[String, GossipMessage]] = {
      IO.pure(Left(bloomfilter.toString))
    }

    def unknownTransactions(transactionHashes: Seq[UInt256Bytes]): IO[Either[String, Seq[Transaction.Verifiable]]] = {
      IO.pure(Either.cond(transactionHashes === sampleTransactionHashes,
        sampleTransactions,
        s"Incorrect input: $transactionHashes"
      ))
    }

    def state(stateRoot: UInt256Bytes): IO[Either[String, Option[State]]] = IO.pure(Right(
      if (stateRoot === sampleStateRoot) Some(sampleState) else None
    ))

    def block(blockHash: UInt256Bytes): IO[Either[String, Option[Block]]] = IO.pure(Right(
      if (blockHash === sampleBlockHash) Some(sampleBlock) else None
    ))
  })

  val service = (endpoint.Status
    :+: endpoint.BloomFilter
    :+: endpoint.UnknownTransactions
    :+: endpoint.State
    :+: endpoint.Block
  ).toService

  def withTestServerAndClient[A](testBody: GossipClient[Future] => Future[A]): A = {

    val newTestServer: ListeningServer = Http.server
      .withStreaming(enabled = true)
      .serve(new InetSocketAddress(InetAddress.getLoopbackAddress, 0), service)

    val newClient: GossipClientInterpreter = {
      val port = refineV[PortRange](newTestServer.boundAddress.asInstanceOf[InetSocketAddress].getPort()).toOption.get
      new GossipClientInterpreter("127.0.0.1", port)
    }

    val future = for {
      result <- testBody(newClient)
      _ <- newClient.close()
      _ <- newTestServer.close()
    } yield result

    Await.result(future, 3.seconds)
  }

  val tests = Tests {
    test("status") - withTestServerAndClient{ client =>
      for {
        response <- client.status
      } yield assert(response === Right(sampleNodeStatus))
    }

    test("bloomfilter") - withTestServerAndClient{ client =>

      val sampleBloomFilter = Arbitrary(for {
        keccak256s <- arbitraryList[UInt256Bytes].arbitrary
      } yield BloomFilter.from(keccak256s)).arbitrary.pureApply(Gen.Parameters.default, Seed.random())

      val expected = s"""Response("HTTP/1.1 Status(500)"): {"message":"${sampleBloomFilter.toString}"}"""

      for {
        response <- client.bloomfilter(sampleBloomFilter)
      } yield assert(response === Left(expected))
    }

    test("unknownTransactions") - withTestServerAndClient{ client =>
      for {
        response <- client.unknownTransactions(sampleTransactionHashes)
      } yield assert(response === Right(sampleTransactions))
    }

    test("state") - withTestServerAndClient{ client =>
      for {
        response <- client.state(sampleStateRoot)
      } yield assert(response === Right(Some(sampleState)))
    }

    test("block") - withTestServerAndClient{ client =>
      for {
        response <- client.block(sampleBlockHash)
      } yield assert(response === Right(Some(sampleBlock)))
    }
  }
}
