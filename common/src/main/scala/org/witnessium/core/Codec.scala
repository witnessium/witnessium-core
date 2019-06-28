package org.witnessium.core

import scodec.bits.ByteVector

trait Decoder[+A] {
  def decode(bytes: ByteVector): Either[String, DecodeResult[A]]
}

final case class DecodeResult[+A](a: A, remainder: ByteVector)

trait Encoder[-A] {
  def encode(a: A): ByteVector
}

trait Codec[A] extends Decoder[A] with Encoder[A]
