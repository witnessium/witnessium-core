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
import scodec.bits.ByteVector
import codec.byte.ByteEncoder
import codec.circe._
import datatype.{UInt256Bytes, UInt256Refine}
import endpoint._
import model.{GossipMessage, ModelArbitrary, NodeStatus, State, Transaction}
import p2p.BloomFilter
import service.GossipService

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.rng.Seed
import utest._

object GossipClientInterpreterTest extends TestSuite with ModelArbitrary {

  private def hash[A: ByteEncoder](a: A): UInt256Bytes = {
    val bytes = ByteEncoder[A].encode(a)
    val hash = ByteVector.view(crypto.keccak256(bytes.toArray))
    UInt256Refine.from(hash).toOption.get
  }

  val sampleNodeStatus = arbitraryNodeStatus.arbitrary.pureApply(Gen.Parameters.default, Seed.random())

  val sampleTransactions = Arbitrary(for {
    count <- Gen.choose(1, 10)
    transactions <- Gen.listOfN(count, arbitrarySigned[Transaction].arbitrary)
  } yield transactions).arbitrary.pureApply(Gen.Parameters.default, Seed.random())

  val sampleTransactionHashes = sampleTransactions.map{ signedTransaction => hash(signedTransaction.value) }

  val sampleState = arbitraryState.arbitrary.pureApply(Gen.Parameters.default, Seed.random())

  val sampleStateRoot = hash(sampleState)

  val endpoint = new GossipEndpoint(new GossipService[IO] {
    def status: IO[Either[String, NodeStatus]] = {
      IO.pure(Right(sampleNodeStatus))
    }

    def bloomfilter(bloomfilter: BloomFilter): IO[Either[String, GossipMessage]] = {
      IO.pure(Left(bloomfilter.toString))
    }

    def unknownTransactions(transactionHashes: Seq[UInt256Bytes]): IO[Either[String, Seq[Transaction.Signed]]] = {
      IO.pure(Either.cond(transactionHashes === sampleTransactionHashes,
        sampleTransactions,
        s"Incorrect input: $transactionHashes"
      ))
    }

    def state(stateRoot: UInt256Bytes): IO[Either[String, State]] = IO.pure{
      Either.cond(stateRoot === sampleStateRoot,
        sampleState,
        s"Not Found: $stateRoot"
      )
    }
  })

  val service = {
    endpoint.Status :+: endpoint.BloomFilter :+: endpoint.UnknownTransactions :+: endpoint.State
  }.toService

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
      } yield assert(response === Right(sampleState))
    }
  }
}
