package org.witnessium.core
package model

import scala.math.Ordering
import cats.implicits._
import eu.timepit.refined.{refineMV, refineV}
import eu.timepit.refined.numeric.NonNegative
import scodec.bits.{Bases, ByteVector}
import datatype.BigNat

import codec.byte.{ByteDecoder, ByteEncoder}

sealed trait Account

object Account {

  final case class Named(
    name: Name,
  ) extends Account

  final case class Unnamed(
    address: Address,
  ) extends Account

  final case class Name private[model] (bytes: ByteVector) extends AnyVal {
    override def toString: String = bytes.toBase64(NameBase64)
  }

  object Name {
    def from(string: String): Either[String, Name] = {
      ByteVector.fromBase64Descriptive(string, NameBase64) match {
        case Right(bytes) if bytes.nonEmpty => Right(Name(bytes))
        case Left(msg) => Left(msg)
        case _ => Left(s"Empty Name: $string")
      }
    }

    implicit val nameOrder: Ordering[Name] = Ordering.by(_.toString)
  }

  object NameBase64 extends Bases.Base64Alphabet {
    private val Chars = (('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9') :+ '-' :+ '.').toArray
    val pad = '='
    def toChar(i: Int) = Chars(i)
    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def toIndex(c: Char) = c match {
      case c if c >= 'A' && c <= 'Z' => c - 'A'
      case c if c >= 'a' && c <= 'z' => c - 'a' + 26
      case c if c >= '0' && c <= '9' => c - '0' + 26 + 26
      case '-' => 62
      case '.' => 63
      case c@_ => throw new IllegalArgumentException
    }
    def ignore(c: Char) = c.isWhitespace
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  implicit val accountEncoder: ByteEncoder[Account] = {
    case Named(name) =>
      val nameBytes = name.bytes
      val nameSizeBytes = ByteEncoder[BigNat].encode(refineV[NonNegative](BigInt(nameBytes.size)).toOption.get)
      nameSizeBytes ++ nameBytes
    case Unnamed(address) =>
      val bignatZero = ByteEncoder[BigNat].encode(refineMV[NonNegative](BigInt(0)))
      bignatZero ++ ByteEncoder[Address].encode(address)
  }

  implicit val accountDecoder: ByteDecoder[Account] = ByteDecoder[BigNat].flatMap {
    case size if size.value === BigInt(0) => ByteDecoder[Address].map(Unnamed(_))
    case size => for {
      name <- ByteDecoder.fromFixedSizeBytes(size.value.toLong)(new Name(_))
    } yield Named(name)
  }
}
