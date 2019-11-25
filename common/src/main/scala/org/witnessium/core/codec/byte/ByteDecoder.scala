package org.witnessium.core
package codec.byte

import java.time.Instant
import scala.reflect._

import eu.timepit.refined.{refineMV, refineV}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.NonNegative
import scodec.bits.{BitVector, ByteVector}
import shapeless.{::, Generic, HList, HNil, Lazy, Nat, Sized}
import shapeless.nat._16
import shapeless.ops.nat.ToInt
import shapeless.syntax.sized._

import datatype.{BigNat, MerkleTrieNode, UInt256BigInt, UInt256Bytes, UInt256Refine}
import model.{Address, Genesis, Signature, Signed, Verifiable}
import util.refined.bitVector._

trait ByteDecoder[A] {
  def decode(bytes: ByteVector): Either[String, DecodeResult[A]]

  def map[B](f: A => B): ByteDecoder[B] = { bytes =>
    decode(bytes).map{ case DecodeResult(a, remainder) => DecodeResult(f(a), remainder) }
  }

  def emap[B](f: A => Either[String, B]): ByteDecoder[B] = { bytes =>
    for{
      decoded <- decode(bytes)
      converted <- f(decoded.value)
    } yield DecodeResult(converted, decoded.remainder)
  }

  def flatMap[B](f: A => ByteDecoder[B]): ByteDecoder[B] = { bytes =>
    decode(bytes).flatMap{ case DecodeResult(a, remainder) =>
      f(a).decode(remainder)
    }
  }
}

final case class DecodeResult[+A](value: A, remainder: ByteVector)

object ByteDecoder {

  def apply[A](implicit bd: ByteDecoder[A]): ByteDecoder[A] = bd

  implicit val hnilByteDecoder: ByteDecoder[HNil] = { bytes =>
    Right[String, DecodeResult[HNil]](DecodeResult(HNil, bytes))
  }

  @com.github.ghik.silencer.silent("is never used")
  implicit def hlistByteDecoder[H, T <: HList](implicit
    bdh: Lazy[ByteDecoder[H]],
    bdt: ByteDecoder[T],
  ): ByteDecoder[H :: T] = { bytes =>
    for {
      decodedH <- bdh.value.decode(bytes)
      decodedT <- bdt.decode(decodedH.remainder)
    } yield DecodeResult(decodedH.value :: decodedT.value, decodedT.remainder)
  }

  implicit def genericDecoder[A, B <: HList](implicit
    agen: Generic.Aux[A, B],
    bdb: Lazy[ByteDecoder[B]]
  ): ByteDecoder[A] = bdb.value map agen.from

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def nat(bigint: BigInt): BigNat = refineV[NonNegative](bigint) match {
    case Right(bignat) => bignat
    case Left(msg) => throw new Exception(msg)
  }

  implicit val bignatDecoder: ByteDecoder[BigNat] = { bytes =>
    Either.cond( bytes.nonEmpty, bytes, "Empty bytes").flatMap { nonEmptyBytes =>
      val head: Int = nonEmptyBytes.head & 0xff
      val tail: ByteVector = nonEmptyBytes.tail
      if (head < 0x80) Right[String, DecodeResult[BigNat]](DecodeResult(nat(BigInt(head)), tail))
      else if (head <= 0xf8) {
        val size = head - 0x80
        if (tail.size < size) Left[String, DecodeResult[BigNat]](s"required byte size $size, but $tail")
        else {
          val (front, back) = tail.splitAt(size.toLong)
          Right[String, DecodeResult[BigNat]](DecodeResult(nat(BigInt(1, front.toArray)), back))
        }
      } else {
        val sizeOfNumber = head - 0xf8 + 1
        if (tail.size < sizeOfNumber) Left[String, DecodeResult[BigNat]](s"required byte size $sizeOfNumber, but $tail")
        else {
          val (sizeBytes, data) = tail.splitAt(sizeOfNumber.toLong)
          val size = BigInt(1, sizeBytes.toArray).toLong
        
          if (data.size < size) Left[String, DecodeResult[BigNat]](s"required byte size $size, but $data")
          else {
            val (front, back) = data.splitAt(size)
            Right[String, DecodeResult[BigNat]](DecodeResult(nat(BigInt(1, front.toArray)), back))
          }
        }
      }
    }
  }

  implicit def verifiableDecoder[A: ByteDecoder]: ByteDecoder[Verifiable[A]] = { bytes =>
    val head: Int = bytes.head.toInt
    if (head >= 64) ByteDecoder[A].map(Genesis(_)).decode(bytes.tail)
    else ByteDecoder[Signed[A]].decode(bytes)
  }

  def sizedListDecoder[A: ByteDecoder](size: BigNat): ByteDecoder[List[A]] = { bytes =>
    @annotation.tailrec
    def loop(
      bytes: ByteVector,
      count: BigInt,
      acc: List[A]
    ): Either[String, DecodeResult[List[A]]] = {
      if (count === refineMV[NonNegative](BigInt(0))) {
        Right[String, DecodeResult[List[A]]](DecodeResult(acc.reverse, bytes))
      } else ByteDecoder[A].decode(bytes) match {
        case Left(msg) => Left[String, DecodeResult[List[A]]](msg)
        case Right(DecodeResult(value, remainder)) => loop(remainder, nat(count - 1), value :: acc)
      }
    }
    loop(bytes, size, Nil)
  }

  implicit def listDecoder[A: ByteDecoder]: ByteDecoder[List[A]] = ByteDecoder[BigNat] flatMap sizedListDecoder[A]

  implicit def setDecoder[A: ByteDecoder]: ByteDecoder[Set[A]] = ByteDecoder[List[A]].map(_.toSet)

  implicit def mapDecoder[A: ByteDecoder, B: ByteDecoder]: ByteDecoder[Map[A, B]] = ByteDecoder[List[(A, B)]].map(_.toMap)

  def fromFixedSizeBytes[T: ClassTag](size: Long)(f: ByteVector => T): ByteDecoder[T] = { bytes => Either.cond( bytes.size >= size,
    bytes splitAt size match { case (front, back) => DecodeResult(f(front), back) },
    s"Too shord bytes to decode ${classTag[T]}; required $size bytes, but receiced ${bytes.size} bytes: $bytes"
  )}

  implicit val variableBytes: ByteDecoder[ByteVector] = bignatDecoder.flatMap(bignat => fromFixedSizeBytes(bignat.toLong)(identity))

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter","org.wartremover.warts.OptionPartial"))
  def fixedSizedVectorDecoder[A: ByteDecoder](size: Nat)(implicit toInt: ToInt[size.N]): ByteDecoder[Vector[A] Sized size.N] =
    sizedListDecoder[A](refineV[NonNegative](BigInt(toInt())).toOption.get).map(_.toVector.sized(size).get)

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter", "org.wartremover.warts.TraversableOps"))
  def fixedSizedOptionalVectorDecoder[A: ByteDecoder](size: Nat)(implicit
    toInt: ToInt[size.N]
  ): ByteDecoder[Vector[Option[A]] Sized size.N] = fromFixedSizeBytes(((toInt() + 7) / 8).toLong)(_.bits).flatMap{ bits =>
    sizedListDecoder[A](nat(BigInt(bits.populationCount))).emap{ list =>
      @annotation.tailrec
      def loop(bits: BitVector, list: List[A], acc: List[Option[A]]): Either[String, List[Option[A]]] = {
        if (bits.isEmpty) Right(acc.reverse) else (list match {
          case _ if !bits.head => loop(bits.tail, list, None :: acc)
          case Nil => Left(s"Not enough bytes: $bits $list $acc")
          case _ => loop(bits.tail, list.tail, Some(list.head) :: acc)
        })
      }
      loop(bits, list, List.empty).map(_.toVector.ensureSized[size.N])
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  implicit val addressDecoder: ByteDecoder[Address] = fromFixedSizeBytes(20){ Address(_).toOption.get }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  implicit val uint256bigintDecoder: ByteDecoder[UInt256BigInt] = fromFixedSizeBytes(32){
    bytes => UInt256Refine.from(BigInt(1, bytes.toArray)).toOption.get
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  implicit val uint256bytesDecoder: ByteDecoder[UInt256Bytes] = fromFixedSizeBytes(32){ UInt256Refine.from(_).toOption.get }

  implicit val byteDecoder: ByteDecoder[Byte] = fromFixedSizeBytes(1)(_.toByte())

  implicit val longDecoder: ByteDecoder[Long] = fromFixedSizeBytes(8)(_.toLong())

  implicit val instantDecoder: ByteDecoder[Instant] = ByteDecoder[Long] map Instant.ofEpochMilli

  implicit val sigHeaderRangeDecoder: ByteDecoder[Int Refined Signature.HeaderRange] = { bytes =>
    ByteDecoder[Byte].decode(bytes).flatMap { case DecodeResult(b, remainder) =>
      refineV[Signature.HeaderRange](b.toInt).map( DecodeResult(_, remainder) )
    }
  }

  implicit val merkleTrieDecoder: ByteDecoder[MerkleTrieNode] = for{
    byte <- byteDecoder
    uint8 = byte & 0xff
    numberOfNibble = (uint8 % (1 << 7)).toLong
    prefixRefined <- fromFixedSizeBytes((numberOfNibble + 1) / 2){
      _.bits.take(numberOfNibble * 4)
    }.emap{ prefix => refineV[MerkleTrieNode.PrefixCondition](prefix)}
    decoder <- ((uint8 >> 7) match {
      case 0 =>
        fixedSizedOptionalVectorDecoder[UInt256Bytes](_16).map(MerkleTrieNode.Branch(prefixRefined, _))
      case 1 =>
        variableBytes.map(MerkleTrieNode.Leaf(prefixRefined, _))
    })
  } yield decoder
}
