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
    val encoded = codec.encode(value)
    val decodeResult = codec.decode(encoded)
    val isMatched = decodeResult === Right(DecodeResult(value, ByteVector.empty))
    if (!isMatched) {
      println(s"===> value: $value")
      println(s"===> encoded: $encoded")
      println(s"===> decode result: $decodeResult")
    }
    isMatched
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

    test("Verifiable Transaction concrete case") {

      import eu.timepit.refined.{refineMV, refineV}
      import eu.timepit.refined.numeric.NonNegative
      def bignat(n: Int): datatype.BigNat = refineV[NonNegative](BigInt(n)).toOption.get
      def r256(bigint: BigInt): datatype.UInt256BigInt = UInt256Refine.from(bigint).toOption.get

      val tx = Signed(
        Signature(
          refineMV[Signature.HeaderRange](28),
          r256(BigInt("78702821254550430007888754717783605726298541177436287971817307962920509992446")),
          r256(BigInt("82900471208263934279268822053523576306113937456112883484338259515115000889819"))
        ),
        Transaction(
          bignat(101),
          Set(ByteVector
            .fromHexDescriptive("0x37df93d26b1741a0efa601320ebc9b1e54a7cc7db1f43d92bd3728695eaa5eb5")
            .flatMap(UInt256Refine.from[ByteVector])
            .toOption.get
          ),
          Set((Address.fromHex("eb40bc3f706fcd6bb08c9f70a76111f2353a3553").toOption.get,bignat(10000)))
        )
      )
      val bytes = ByteEncoder[Transaction.Verifiable].encode(tx)
      val decoded = ByteDecoder[Transaction.Verifiable].decode(bytes)
      assert(decoded === Right(DecodeResult(tx, ByteVector.empty)))
    }
  }
}
