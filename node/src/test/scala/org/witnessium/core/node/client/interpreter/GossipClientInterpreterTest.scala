package org.witnessium.core
package node
package client
package interpreter

import java.net.{InetAddress, InetSocketAddress}
import cats.effect.IO
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.{Http, ListeningServer}
import com.twitter.util.Await
import eu.timepit.refined.refineV
import io.circe.generic.auto._
import io.circe.refined._
import io.finch.circe._
import codec.circe._
import datatype.UInt256Bytes
import endpoint._
import model.{GossipMessage, ModelArbitrary, NodeStatus}
import p2p.BloomFilter
import service.GossipService

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.rng.Seed
import utest._

object GossipClientInterpreterTest extends TestSuite with ModelArbitrary {

  val sampleNodeStatus = arbitraryNodeStatus.arbitrary.pureApply(Gen.Parameters.default, Seed.random())

  val endpoint = new GossipEndpoint(new GossipService[IO] {
    def status: IO[Either[String, NodeStatus]] = {
      IO.pure(Right(sampleNodeStatus))
    }

    def bloomfilter(bloomfilter: BloomFilter): IO[Either[String, GossipMessage]] = {
      IO.pure(Left(bloomfilter.toString))
    }
  })

  val service = {
    endpoint.Status :+: endpoint.BloomFilter
  }.toService

  def newTestServer = Http.server
    .withStreaming(enabled = true)
    .serve(new InetSocketAddress(InetAddress.getLoopbackAddress, 0), service)

  def newClient(server: ListeningServer): GossipClientInterpreter = {
    val port = refineV[PortRange](server.boundAddress.asInstanceOf[InetSocketAddress].getPort()).toOption.get
    new GossipClientInterpreter("127.0.0.1", port)
  }

  val tests = Tests {
    test("status") {

      val testServer = newTestServer
      val client = newClient(testServer)

      val responseFuture = for {
        response <- client.status
        _ <- client.close()
        _ <- testServer.close()
      } yield response

      val resp = Await.result(responseFuture, 3.seconds)

      assert(resp === Right(sampleNodeStatus))
    }

    test("bloomfilter") {

      val testServer = newTestServer
      val client = newClient(testServer)

      val sampleBloomFilter = Arbitrary(for {
        keccak256s <- arbitraryList[UInt256Bytes].arbitrary
      } yield BloomFilter.from(keccak256s)).arbitrary.pureApply(Gen.Parameters.default, Seed.random())

      val expected = s"""Response("HTTP/1.1 Status(500)"): {"message":"${sampleBloomFilter.toString}"}"""

      val responseFuture = for {
        response <- client.bloomfilter(sampleBloomFilter)
        _ <- client.close()
        _ <- testServer.close()
      } yield response

      val resp = Await.result(responseFuture, 3.seconds)

      assert(resp === Left(expected))
    }
  }
}
