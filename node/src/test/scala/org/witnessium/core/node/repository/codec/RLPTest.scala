package org.witnessium.core
package node.repository
package codec

import scodec.DecodeResult
import scodec.bits.{BitVector, ByteVector}
import RLP.codec.{encode, decode}
import utest._

object RLPTest extends TestSuite {
  val tests = Tests {

    "encode" - {
      """string "dog"""" - {
        val encoded = encode(RlpString("dog")).require
        assert(encoded == BitVector(0x83, 'd', 'o', 'g'))
      }
      """list ["cat", "dog"]""" - {
        val encoded = encode(RlpList(RlpString("cat"), RlpString("dog"))).require
        assert(encoded == BitVector(0xc8, 0x83, 'c', 'a', 't', 0x83, 'd', 'o', 'g'))
      }

      "empty string" - {
        assert(encode(RlpString.empty).require == BitVector(0x80))
      }

      "empty list" - {
        assert(encode(RlpList.empty).require == BitVector(0xc0))
      }

      "the integer 0" - {
        assert(encode(RlpString(0)).require == BitVector(0x80))
      }

      "the encoded integer 0 (0x00)" - {
        assert(encode(RlpString(ByteVector(0x00))).require == BitVector(0x00))
      }

      "the encoded integer 15 (0x0f)" - {
        assert(encode(RlpString(ByteVector(0x0f))).require == BitVector(0x0f))
      }

      "the encoded integer 1024 (0x04 0x00)" - {
        assert(encode(RlpString(ByteVector(0x04, 0x00))).require == BitVector(0x82, 0x04, 0x00))
      }

      "the set theoretical representation of three, [ [], [[]], [ [], [[]] ] ]" - {
        val zero = RlpList.empty
        val one = RlpList(zero)
        val two = RlpList(zero, one)
        val three = RlpList(zero, one, two)
        assert(encode(three).require == BitVector(0xc7, 0xc0, 0xc1, 0xc0, 0xc3, 0xc0, 0xc1, 0xc0))
      }

      """The string "Lorem ipsum dolor sit amet, consectetur adipisicing elit"""" - {
        val lorem = "Lorem ipsum dolor sit amet, consectetur adipisicing elit"
        assert(encode(RlpString(lorem)).require == BitVector(0xb8, 0x38) ++ BitVector(lorem.getBytes))
      }
    }

    "decode" - {
      """string "dog"""" - {
        val decoded = decode(BitVector(0x83, 'd', 'o', 'g')).require
        val expected = DecodeResult(RlpString(ByteVector("dog".getBytes)), BitVector.empty)
        assert(decoded == expected)
      }

      """list ["cat", "dog"]""" - {
        val decoded = decode(BitVector(0xc8, 0x83, 'c', 'a', 't', 0x83, 'd', 'o', 'g')).require
        val expected = DecodeResult(RlpList(RlpString("cat"), RlpString("dog")), BitVector.empty)
        assert(decoded == expected)
      }

      "empty string / integer 0" - {
        assert(decode(BitVector(0x80)).require == DecodeResult(RlpString.empty, BitVector.empty))
      }

      "empty list" - {
        assert(decode(BitVector(0xc0)).require == DecodeResult(RlpList.empty, BitVector.empty))
      }

      "the encoded integer 0 (0x00)" - {
        assert(decode(BitVector(0x00)).require == DecodeResult(RlpString(ByteVector(0x00)), BitVector.empty))
      }

      "the encoded integer 15 (0x0f)" - {
        assert(decode(BitVector(0x0f)).require == DecodeResult(RlpString(ByteVector(0x0f)), BitVector.empty))
      }

      "the encoded integer 1024 (0x04 0x00)" - {
        assert(decode(BitVector(0x82, 0x04, 0x00)).require == DecodeResult(RlpString(ByteVector(0x04, 0x00)), BitVector.empty))
      }

      "the set theoretical representation of three, [ [], [[]], [ [], [[]] ] ]" - {
        val zero = RlpList.empty
        val one = RlpList(zero)
        val two = RlpList(zero, one)
        val three = RlpList(zero, one, two)
        assert(decode(BitVector(0xc7, 0xc0, 0xc1, 0xc0, 0xc3, 0xc0, 0xc1, 0xc0)).require == DecodeResult(three, BitVector.empty))
      }

      """The string "Lorem ipsum dolor sit amet, consectetur adipisicing elit"""" - {
        val lorem = "Lorem ipsum dolor sit amet, consectetur adipisicing elit"
        assert(decode(BitVector(0xb8, 0x38) ++ BitVector(lorem.getBytes)).require == DecodeResult(RlpString(lorem), BitVector.empty))
      }
    }
  }
}
