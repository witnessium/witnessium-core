package org.witnessium.core
package node
package client
package interpreter

import java.net.{InetAddress, InetSocketAddress}
import cats.effect.IO
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Http
import com.twitter.util.Await
import eu.timepit.refined.refineV
import io.circe.generic.auto._
import io.circe.refined._
import io.finch.circe._
import codec.circe._
import endpoint.GossipEndpoint
import model.{ModelArbitrary, NodeStatus}
import service.GossipService

import org.scalacheck.Gen
import org.scalacheck.rng.Seed
import utest._

object GossipClientInterpreterTest extends TestSuite with ModelArbitrary {

  val tests = Tests {
    test("status") {
      val sampleNodeStatus = arbitraryNodeStatus.arbitrary.pureApply(Gen.Parameters.default, Seed.random())

      val endpoint = new GossipEndpoint(new GossipService[IO] {
        def status: IO[Either[String, NodeStatus]] = {
          IO.pure(Right(sampleNodeStatus))
        }
      })

      val testServer = Http.server
        .withStreaming(enabled = true)
        .serve(new InetSocketAddress(InetAddress.getLoopbackAddress, 0), endpoint.Status.toService)

      val port = refineV[PortRange](testServer.boundAddress.asInstanceOf[InetSocketAddress].getPort()).toOption.get

      val client = new GossipClientInterpreter("127.0.0.1", port)

      val responseFuture = for {
        response <- client.status
        _ <- client.close()
        _ <- testServer.close()
      } yield response

      val resp = Await.result(responseFuture, 3.seconds)

      assert(resp === Right(sampleNodeStatus))
    }
  }
}
