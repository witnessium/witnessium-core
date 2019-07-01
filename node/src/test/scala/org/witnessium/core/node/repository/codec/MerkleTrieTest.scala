package org.witnessium.core
package node.repository
package codec

import scodec.DecodeResult
import scodec.bits.{BitVector, ByteVector}
import shapeless.nat._16
import shapeless.syntax.sized._
import utest._

object MerkleTrieTest extends TestSuite {

  val NullHash: UInt256Refine.UInt256Bytes = UInt256Refine.from(ByteVector.low(32)).toOption.get

  val tests = Tests {
    test("codec"){
      val codec = MerkleTrie.codec
      val Some(prefix) = BitVector.fromHex("0123456789ABCDEF" * 4)

      "leaf" - {
        val Right(aValue) = ByteVector.encodeAscii("A_VALUE")
        val leaf = Leaf(prefix, aValue)

        val encoded = codec.encode(leaf).require
        val decoded = codec.decode(encoded).require
        val expected = DecodeResult(leaf, BitVector.empty)

        assert(decoded == expected)
      }

      "branch" - {
        val branch = Branch(prefix, Vector.fill(16)(NullHash).ensureSized[_16])

        val encoded = codec.encode(branch).require
        val decoded = codec.decode(encoded).require
        val expected = DecodeResult(branch, BitVector.empty)

        assert(decoded == expected)
      }
    }
  }
}
