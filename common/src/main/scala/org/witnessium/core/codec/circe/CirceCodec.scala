package org.witnessium.core
package codec.circe

import scala.collection.SortedSet

import io.circe.{Decoder, Encoder}
import scodec.bits.{BitVector, ByteVector}

import model.{Address, Transaction}

trait CirceCodec {

  implicit val circeUInt256BigIntDecoder: Decoder[UInt256Refine.UInt256BigInt] =
    Decoder.decodeString.emap { (str: String) =>
      UInt256Refine.from[BigInt](BigInt(str))
    }

  implicit val circeBitVectorDecoder: Decoder[BitVector] = Decoder.decodeString.emap{ (str: String) =>
    BitVector.fromBase64(str).toRight(s"Base64 decoding failure: $str")
  }

  implicit def circeBitVectorEncoder[A <: BitVector]: Encoder[A] = Encoder.encodeString.contramap[A](_.toBase64)

  implicit val circeByteVectorDecoder: Decoder[ByteVector] = Decoder.decodeString.emap{ (str: String) =>
    ByteVector.fromBase64(str).toRight(s"Base64 decoding failure: $str")
  }

  implicit def circeByteVectorEncoder[A <: ByteVector]: Encoder[A] = Encoder.encodeString.contramap[A](_.toBase64)

  implicit val circeAddressDecoder: Decoder[Address] = Decoder.decodeString.emap{ (str: String) =>
    Address.fromBase64(str)
  }

  implicit val uint8Ordering: Ordering[UInt8] = Ordering.by(_.value)

  implicit val addressOrdering: Ordering[Address] = Ordering.by(_.bytes.toArray.toIterable)

  implicit def circeSortedSetDecoder[A: Decoder: Ordering]: Decoder[SortedSet[A]] = Decoder.decodeSet[A].map(SortedSet.empty[A] ++ _)

  implicit val circeTransactionDecoder: Decoder[Transaction] = implicitly[Decoder[Transaction]]
}
