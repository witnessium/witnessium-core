package org.witnessium.core
package codec.circe

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import io.circe.refined._
import org.scalacheck.{Arbitrary, Prop}
import org.scalacheck.Prop.forAll
import datatype.{UInt256BigInt, UInt256Bytes}
import model._

import test.UTestScalaCheck
import utest._

object CirceCodecTest extends TestSuite with UTestScalaCheck with ModelArbitrary {

  def successfulRoundTrip[A:Arbitrary: Decoder: Encoder]: Prop = forAll { (value: A) =>
    Decoder[A].decodeJson(Encoder[A].apply(value)) === Right(value)
  }

  val tests = Tests {

    test("NetworkId"){
      successfulRoundTrip[NetworkId].checkUTest()
    }

    test("UInt256BigInt") {
      successfulRoundTrip[UInt256BigInt].checkUTest()
    }

    test("UInt256Bytes") {
      successfulRoundTrip[UInt256Bytes].checkUTest()
    }

    test("Address"){
      successfulRoundTrip[Address].checkUTest()
    }

    test("Transaction"){
      successfulRoundTrip[Transaction].checkUTest()
    }

    test("Signature"){
      successfulRoundTrip[Signature].checkUTest()
    }

    test("Verifiable Transaction"){
      successfulRoundTrip[Transaction.Verifiable].checkUTest()
    }

    test("BlockHeader"){
      successfulRoundTrip[BlockHeader].checkUTest()
    }

    test("Block"){
      successfulRoundTrip[Block].checkUTest()
    }

    test("GossipMessage"){
      successfulRoundTrip[GossipMessage].checkUTest()
    }

    test("NodeStatus") {
      successfulRoundTrip[NodeStatus].checkUTest()
    }
  }
}
