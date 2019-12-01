package org.witnessium.core
package codec.circe

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import scala.util.Try
import cats.data.NonEmptyList
import cats.syntax.functor._
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import io.circe.generic.auto._
import io.circe.refined._
import io.circe.syntax._
import scodec.bits.{BitVector, ByteVector}

import datatype.{UInt256BigInt, UInt256Bytes, UInt256Refine}
import model.{Address, Genesis, Signed, Verifiable}

trait CirceCodec {

  implicit val circeUInt256BigIntDecoder: Decoder[UInt256BigInt] =
    Decoder.decodeString.emap { (str: String) =>
      UInt256Refine.from[BigInt](BigInt(str))
    }

  implicit val circeUInt256BigIntEncoder: Encoder[UInt256BigInt] = Encoder.encodeString.contramap(_.toString)

  implicit val circeUInt256BytesDecoder: Decoder[UInt256Bytes] = Decoder.decodeString.emap((str: String) => for {
    bytes <- ByteVector.fromHexDescriptive(str)
    refined <- UInt256Refine.from(bytes)
  } yield refined)

  implicit val circeUInt256BytesEncoder: Encoder[UInt256Bytes] = Encoder.encodeString.contramap(_.toHex)

  implicit val circeBitVectorDecoder: Decoder[BitVector] = Decoder.decodeString.emap{ (str: String) =>
    BitVector.fromBase64(str).toRight(s"Base64 decoding failure: $str")
  }

  implicit def circeBitVectorEncoder[A <: BitVector]: Encoder[A] = Encoder.encodeString.contramap[A](_.toBase64)

  implicit val circeByteVectorDecoder: Decoder[ByteVector] = Decoder.decodeString.emap{ (str: String) =>
    ByteVector.fromBase64(str).toRight(s"Base64 decoding failure: $str")
  }

  implicit def circeByteVectorEncoder[A <: ByteVector]: Encoder[A] = Encoder.encodeString.contramap[A](_.toBase64)

  implicit def circeVerifiableDecoder[A : Decoder]: Decoder[Verifiable[A]] = NonEmptyList.of[Decoder[Verifiable[A]]](
    Decoder[Signed[A]].widen,
    Decoder[Genesis[A]].widen,
  ).reduceLeft(_ or _)

  implicit def circeVerifiableEncoder[A : Encoder]: Encoder[Verifiable[A]] = Encoder.instance {
    case signed: Signed[A] => signed.asJson
    case genesis: Genesis[A] => genesis.asJson
  }

  implicit val circeInstantDecoder: Decoder[Instant] = Decoder.decodeString.emap{ (str: String) =>
    Try(OffsetDateTime.parse(str)).toEither.map(_.toInstant()).left.map(_.getMessage)
  }

  implicit val circeInstantEncoder: Encoder[Instant] = Encoder.encodeString.contramap{
    _.atOffset(ZoneOffset.UTC).toString()
  }

  implicit val circeAddressDecoder: Decoder[Address] = Decoder.decodeString.emap{ (str: String) =>
    Address.fromHex(str)
  }

  implicit val circeAddressEncoder: Encoder[Address] = Encoder.encodeString.contramap(_.toString)

  implicit val circeUInt256BytesKeyDecoder: KeyDecoder[UInt256Bytes] = KeyDecoder.instance((str: String) => for {
    bytes <- ByteVector.fromBase64(str)
    refined <- UInt256Refine.from(bytes).toOption
  } yield refined)

  implicit val circeUInt256BytesKeyEncoder: KeyEncoder[UInt256Bytes] = KeyEncoder.instance(_.toBytes.toBase64)

}
