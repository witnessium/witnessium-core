package org.witnessium.core
package node.p2p

import scala.util.hashing.MurmurHash3
import scodec.bits.BitVector
import datatype.UInt256Bytes

final case class BloomFilter(bits: BitVector, numberOfHash: Int) {
  def check(keccak256: UInt256Bytes): Boolean = BloomFilter.hashes(numberOfHash)(keccak256) forall bits.get
}

object BloomFilter {

  val NumberOfBits: Int = 65536

  def from(keccak256s: Seq[UInt256Bytes]): BloomFilter = {

    val numberOfHash = math.round(math.log(2) * NumberOfBits / keccak256s.size).toInt min 20

    val hashValues: Seq[Long] = keccak256s flatMap hashes(numberOfHash)

    BloomFilter((BitVector.low(NumberOfBits.toLong) /: hashValues)(_ set _), numberOfHash)
  }

  private[p2p] def hashes(numberOfHash: Int)(keccak256: UInt256Bytes): Seq[Long] = for {
    i <- 0 until numberOfHash
    init = keccak256.take(4).toInt()
    murmur = MurmurHash3.bytesHash(keccak256.toArray)
  } yield ((init + i * murmur) % NumberOfBits + NumberOfBits) % NumberOfBits.toLong

}
