package org.witnessium.core
package node.p2p

import scala.util.Random
import scodec.bits.ByteVector
import datatype.{UInt256Bytes, UInt256Refine}
import utest._

object BloomFilterTest extends TestSuite {

  def generateKeccak256s(numberOfKeccak256: Int): Seq[UInt256Bytes] = {
    val bytes = Array.ofDim[Byte](32 * numberOfKeccak256)
    Random.nextBytes(bytes)

    for(i <- 0 until numberOfKeccak256) yield {
      UInt256Refine.from(ByteVector.view(bytes, i * 32, 32)).toOption.get
    }
  }

  val tests = Tests {
    test("check correctly generated"){

      val numberOfKeccak256 = Random.nextInt(6000)

      val keccak256s = generateKeccak256s(numberOfKeccak256)
      val testKeccak256s = generateKeccak256s(numberOfKeccak256)

      val bloomFilter = BloomFilter.from(keccak256s)

      val falsePositives = testKeccak256s.count(bloomFilter.check)
      val rate = falsePositives.toDouble / numberOfKeccak256
      println(s"False positive rate of the BloomFilter: $falsePositives / $numberOfKeccak256 = $rate")
      assert(keccak256s forall bloomFilter.check)
    }
  }
}
