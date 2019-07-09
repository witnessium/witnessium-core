package org.witnessium.core
package codec.circe

import java.time.Instant
import io.circe.{Decoder, Encoder}
import scodec.bits.{BitVector, ByteVector}

import datatype.{UInt256BigInt, UInt256Bytes, UInt256Refine}
import model.Address

trait CirceCodec {

  implicit val circeUInt256BigIntDecoder: Decoder[UInt256BigInt] =
    Decoder.decodeString.emap { (str: String) =>
      UInt256Refine.from[BigInt](BigInt(str))
    }

  implicit val circeUInt256BigIntEncoder: Encoder[UInt256BigInt] = Encoder.encodeString.contramap(_.toString)

  implicit val circeUInt256BytesDecoder: Decoder[UInt256Bytes] = Decoder.decodeString.emap((str: String) => for {
    bytes <- ByteVector.fromBase64Descriptive(str)
    refined <- UInt256Refine.from(bytes)
  } yield refined)

  implicit val circeBitVectorDecoder: Decoder[BitVector] = Decoder.decodeString.emap{ (str: String) =>
    BitVector.fromBase64(str).toRight(s"Base64 decoding failure: $str")
  }

  implicit def circeBitVectorEncoder[A <: BitVector]: Encoder[A] = Encoder.encodeString.contramap[A](_.toBase64)

  implicit val circeByteVectorDecoder: Decoder[ByteVector] = Decoder.decodeString.emap{ (str: String) =>
    ByteVector.fromBase64(str).toRight(s"Base64 decoding failure: $str")
  }

  implicit def circeByteVectorEncoder[A <: ByteVector]: Encoder[A] = Encoder.encodeString.contramap[A](_.toBase64)

  implicit val circeInstantDecoder: Decoder[Instant] = Decoder.decodeLong.map(Instant.ofEpochMilli)

  implicit val circeInstantEncoder: Encoder[Instant] = Encoder.encodeLong.contramap(_.toEpochMilli)

  implicit val circeAddressDecoder: Decoder[Address] = Decoder.decodeString.emap{ (str: String) =>
    Address.fromBase64(str)
  }

  implicit val circeAddressEncoder: Encoder[Address] = circeByteVectorEncoder.contramap[Address](_.bytes)
}
