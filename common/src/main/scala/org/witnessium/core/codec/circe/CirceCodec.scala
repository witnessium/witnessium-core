package org.witnessium.core
package codec.circe

import io.circe.{Decoder, Encoder}
import scodec.bits.ByteVector

trait CirceCodec {

  implicit val circeUInt256BigIntDecoder: Decoder[UInt256Refine.UInt256BigInt] =
    Decoder.decodeString.emap { (str: String) =>
      UInt256Refine.from[BigInt](BigInt(str))
    }

  implicit def circeByteVectorEncoder[A <: ByteVector]: Encoder[A] = Encoder.encodeString.contramap[A](_.toBase64)
}
