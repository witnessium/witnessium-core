package org.witnessium.core
package codec.byte

import java.time.Instant

import eu.timepit.refined.refineV
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.NonNegative
import scodec.bits.{BitVector, ByteVector}
import shapeless.{::, Generic, HList, HNil, Lazy}

import datatype.{BigNat, MerkleTrieNode, UInt256Refined, UInt256Refine}
import UInt256Refine.UInt256RefineOps
import model.{Address, Genesis, Signature, Signed, Verifiable}

trait ByteEncoder[A] {
  def encode(a: A): ByteVector

  def contramap[B](f: B => A): ByteEncoder[B] = { b => encode(f(b)) }
}

object ByteEncoder {
  def apply[A](implicit be: ByteEncoder[A]): ByteEncoder[A] = be

  implicit val hnilByteEncoder: ByteEncoder[HNil] = { _ => ByteVector.empty }

  implicit def hlistByteEncoder[H, T <: HList](implicit
    beh: Lazy[ByteEncoder[H]],
    bet: ByteEncoder[T],
  ): ByteEncoder[H :: T] = { case h :: t => beh.value.encode(h) ++ bet.encode(t) }

  implicit def genericEncoder[A, B <: HList](implicit
    agen: Generic.Aux[A, B],
    beb: Lazy[ByteEncoder[B]]
  ): ByteEncoder[A] = beb.value contramap agen.to

  implicit val bignatEncoder: ByteEncoder[BigNat] = { bignat =>
    val bytes = ByteVector.view(bignat.toByteArray).dropWhile(_ === 0x00.toByte)
    if (bytes.isEmpty) ByteVector(0x00.toByte)
    else if (bignat <= 0x80) bytes
    else {
      val size = bytes.size
      if (size < (0xf8 - 0x80) + 1) ByteVector.fromByte((size + 0x80).toByte) ++ bytes
      else {
        val sizeBytes = ByteVector.fromLong(size).dropWhile(_ === 0x00.toByte)
        ByteVector.fromByte((sizeBytes.size + 0xf8 - 1).toByte) ++ sizeBytes ++ bytes
      }
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def nat(bigint: BigInt): BigNat = refineV[NonNegative](bigint) match {
    case Right(bignat) => bignat
    case Left(msg) => throw new Exception(msg)
  }

  implicit def verifiableEncoder[A: ByteEncoder]: ByteEncoder[Verifiable[A]] = {
    case signed: Signed[A] => ByteEncoder[Signed[A]].encode(signed)
    case Genesis(value) => 64.toByte +: ByteEncoder[A].encode(value)
  }

  implicit def listEncoder[A: ByteEncoder]: ByteEncoder[List[A]] = { list =>
    (ByteEncoder[BigNat].encode(nat(BigInt(list.size))) /: list.map(ByteEncoder[A].encode))(_ ++ _)
  }

  private def sortedListEncoder[A: ByteEncoder]: ByteEncoder[List[A]] = { list =>
    (ByteEncoder[BigNat].encode(nat(BigInt(list.size))) /: list.map(ByteEncoder[A].encode).sortBy(_.toHex))(_ ++ _)
  }

  implicit val variableBytes: ByteEncoder[ByteVector] = { byteVector =>
    bignatEncoder.contramap((bytes: ByteVector) => nat(bytes.size)).encode(byteVector) ++ byteVector
  }

  implicit def setEncoder[A: ByteEncoder]: ByteEncoder[Set[A]] = sortedListEncoder[A].contramap(_.toList)

  implicit def mapEncoder[A: ByteEncoder, B: ByteEncoder]: ByteEncoder[Map[A, B]] = sortedListEncoder[(A, B)].contramap(_.toList)

  implicit val addressEncoder: ByteEncoder[Address] = _.bytes

  implicit def uint256RefineEncoder[A: UInt256RefineOps]: ByteEncoder[UInt256Refined[A]] = _.toBytes

  implicit val byteEncoder: ByteEncoder[Byte] = ByteVector.fromByte

  implicit val longEncoder: ByteEncoder[Long] = ByteVector.fromLong(_)

  implicit val instantEncoder: ByteEncoder[Instant] = ByteVector fromLong _.toEpochMilli

  implicit val sigHeaderRangeEncoder: ByteEncoder[Int Refined Signature.HeaderRange] = ByteVector fromByte _.toByte

  implicit val merkleTrieEncoder: ByteEncoder[MerkleTrieNode] = { node =>
    val prefixNibbleSize = node.prefix.size / 4
    val (int, bytes) = node match {
      case MerkleTrieNode.Branch(prefix, children) =>
        val existanceBytes = BitVector.bits(children.unsized.map(_.nonEmpty)).bytes
        val concat: ByteVector = (existanceBytes /: children.flatMap(_.toList))(_ ++ _)
        (prefixNibbleSize, prefix.bytes ++ concat)
      case MerkleTrieNode.Leaf(prefix, value) =>
        ((1 << 7) + prefixNibbleSize, prefix.bytes ++ variableBytes.encode(value))
    }
    ByteVector.fromByte(int.toByte) ++ bytes
  }
}
