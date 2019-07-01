package org.witnessium.core
package node.repository
package codec

import scodec.{Attempt, Codec, DecodeResult, Err, SizeBound}
import scodec.bits.{BitVector, ByteVector}

sealed trait RLP
final case class RlpString(value: ByteVector) extends RLP
final case class RlpList(values: Vector[RLP]) extends RLP

object RLP {

  implicit val codec: Codec[RLP] = new Codec[RLP] {

    def encode(rlp: RLP): Attempt[BitVector] = {
      def encodeLength(l: Long, offset: Int): ByteVector = if (l < 56) ByteVector(l.toByte + offset) else {
        val lengthBytes = ByteVector.fromLong(l).dropWhile(_ === 0)
          (lengthBytes.size + offset + 55).toByte +: lengthBytes
      }
      Attempt.successful((rlp match {
        case RlpString(v) if v.size === 1 && v.head <= 0x7f => v
        case RlpString(v) => encodeLength(v.size, 0x80) ++ v
        case RlpList(vs) =>
          @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
          val output = (ByteVector.empty /: vs)(_ ++ encode(_).require.bytes)
          encodeLength(output.size, 0xc0) ++ output
      }).bits)
    }

    def sizeBound: SizeBound = SizeBound.atLeast(8)
    @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
    def decode(bits: BitVector): Attempt[DecodeResult[RLP]] = try{
      val bytes = bits.bytes
      if (bytes.isEmpty) Attempt.successful(DecodeResult(RlpList.empty, bytes.bits)) else {

        val prefix = bytes.head & 0xff

        @annotation.tailrec
        @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments", "org.wartremover.warts.Nothing", "org.wartremover.warts.Recursion"))
        def loop(bytes: ByteVector, acc: Vector[RLP] = Vector.empty): Vector[RLP] = if (bytes.isEmpty) acc else {
          val DecodeResult(rlp, bits) = decode(bytes.bits).require
          loop(bits.bytes, acc :+ rlp)
        }

        def splitFirstPart(offset: Long): (ByteVector, ByteVector) = bytes.tail.splitAt(prefix.toLong - offset)

        def getLengthBytes(offset: Long): (ByteVector, ByteVector) = {
          val (lengthBytes, ramnants) = splitFirstPart(offset)
          ramnants.splitAt(lengthBytes.toLong())
        }

        if (prefix <= 0x7f) {
          Attempt.successful(DecodeResult(RlpString(ByteVector(prefix)), bytes.tail.bits))
        }
        else if (prefix <= 0xb7) {
          val (f, b) = splitFirstPart(0x80)
          Attempt.successful(DecodeResult(RlpString(f), b.bits))
        } else if (prefix <= 0xbf) {
          val (f, b) = getLengthBytes(0xb7)
          Attempt.successful(DecodeResult(RlpString(f), b.bits))
        } else if (prefix <= 0xf7) {
          val (f, b) = splitFirstPart(0xc0)
          Attempt.successful(DecodeResult(RlpList(loop(f)), b.bits))
        } else {
          val (f, b) = getLengthBytes(0xf7)
          Attempt.successful(DecodeResult(RlpList(loop(f)), b.bits))
        }
      }
    } catch {
      case _: Exception => Attempt.failure(Err(s"Fail to decode: $bits"))
    }
  }
}

object RlpString {
  lazy val empty: RlpString = RlpString(ByteVector.empty)

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply(s: String): RlpString = RlpString(ByteVector(s.getBytes))
  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply(i: Int): RlpString = RlpString(ByteVector.fromInt(i).dropWhile(_ == 0))
}

object RlpList {
  lazy val empty: RlpList = RlpList()
  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply(rlps: RLP*): RlpList = RlpList(rlps.toVector)
}
