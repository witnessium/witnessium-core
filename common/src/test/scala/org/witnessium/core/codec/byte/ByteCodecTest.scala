package org.witnessium.core
package codec.byte

import org.scalacheck.{Arbitrary, Prop}
import org.scalacheck.Prop.forAll
import scodec.bits.ByteVector
import datatype.{MerkleTrieNode, UInt256Refine}
import model._

import test.UTestScalaCheck
import utest._

object ByteCodecTest extends TestSuite with UTestScalaCheck with ModelArbitrary {

  def successfulRoundTrip[A:Arbitrary](implicit codec: ByteCodec[A]): Prop = forAll { (value: A) =>
    codec.decode(codec.encode(value)) === Right(DecodeResult(value, ByteVector.empty))
  }

  val tests = Tests {

    test("NetworkId"){
      successfulRoundTrip[NetworkId].checkUTest()
    }

    test("UInt256BigInt") {
      successfulRoundTrip[UInt256Refine.UInt256BigInt].checkUTest()
    }

    test("UInt256Bytes") {
      successfulRoundTrip[UInt256Refine.UInt256Bytes].checkUTest()
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

    test("State"){
      successfulRoundTrip[State].checkUTest()
    }

    test("BlockHeader"){
      successfulRoundTrip[BlockHeader].checkUTest()
    }

    test("Block"){
      successfulRoundTrip[Block].checkUTest()
    }

    test("MerkleTrieNode") {
      successfulRoundTrip[MerkleTrieNode].checkUTest()
    }

    test("NodeStatus") {
      successfulRoundTrip[NodeStatus].checkUTest()
    }
  }
}
