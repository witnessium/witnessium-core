package org.witnessium.core
package node.repository
package codec

import eu.timepit.refined.{W, refineV}
import eu.timepit.refined.collection.Size
import eu.timepit.refined.generic.Equal
import eu.timepit.refined.scodec.byteVector._
import scodec.bits.{BitVector, ByteVector}
import scodec.{Attempt, Codec, DecodeResult, Err}
import scodec.codecs.{bits, discriminated, provide, sizedVector, uint8}
import shapeless.Sized
import shapeless.nat._16

sealed trait MerkleTrie
final case class Branch(prefix: BitVector, children: MerkleTrie.Children) extends MerkleTrie
final case class Leaf(prefix: BitVector, value: ByteVector) extends MerkleTrie

object MerkleTrie {

  type Children = Vector[UInt256Refine.UInt256Bytes] Sized _16

  @SuppressWarnings(Array("org.wartremover.warts.Nothing")) 
  val HashCodec: Codec[UInt256Refine.UInt256Bytes] = Codec(
    uint256 => Attempt.successful(uint256.bits),
    bits => if (bits.size < 256) Attempt.failure(Err.insufficientBits(256, bits.size)) else {
      val (target, remainder) = bits.splitAt(256)
      Attempt.fromEither(refineV[Size[Equal[W.`32L`.T]]](target.bytes)
        .map(_.value.asInstanceOf[UInt256Refine.UInt256Bytes])
        .map(v => DecodeResult(value = v, remainder = remainder))
        .left.map(Err(_))
      )
    }
  )

  val codec: Codec[MerkleTrie] = {

    uint8.flatZip{ int =>

      val prefixCodec: Codec[BitVector] = bits((int % (1 << 7)).toLong * 4)

      val branchCodec: Codec[Branch] = {
        prefixCodec :: sizedVector(_16, HashCodec)
      }.as[Branch]

      @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
      val leafCodec: Codec[Leaf] = {
        prefixCodec :: RLP.codec.narrowc[ByteVector]{
          case RlpString(value) => Attempt.successful(value)
          case l: RlpList => Attempt.failure(Err(s"Not a string: $l"))
        }(bytes => RlpString(bytes))
      }.as[Leaf]

      discriminated[MerkleTrie].by(provide(int >> 7))
        .typecase(0, branchCodec)
        .typecase(1, leafCodec)
    }.xmapc[MerkleTrie]{
      case (_, mt) => mt
    }{
      case b: Branch => ((b.prefix.size / 4).toInt, b)
      case l: Leaf => ((1 << 7) + (l.prefix.size / 4).toInt, l)
    }
  }
}
