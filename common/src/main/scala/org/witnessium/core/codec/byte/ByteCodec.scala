package org.witnessium.core.codec.byte

import scodec.bits.ByteVector

trait ByteCodec[A] extends ByteDecoder[A] with ByteEncoder[A]

object ByteCodec {

  def apply[A](implicit bc: ByteCodec[A]): ByteCodec[A] = bc

  implicit def byteCodec[A](implicit
    decoder: ByteDecoder[A],
    encoder: ByteEncoder[A]
  ): ByteCodec[A] = new ByteCodec[A] {
    override def decode(bytes: ByteVector): Either[String, DecodeResult[A]] = decoder.decode(bytes)
    override def encode(a: A): ByteVector = encoder.encode(a)
  }
}
