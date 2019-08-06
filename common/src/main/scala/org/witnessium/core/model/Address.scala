package org.witnessium.core
package model

import scodec.bits.ByteVector

final case class Address private (bytes: ByteVector) extends AnyVal {
  override def toString: String = bytes.toHex
}

object Address {

  def apply(bytes: ByteVector): Either[String, Address] = Either.cond( bytes.size === 20L,
    new Address(bytes),
    s"Byte size is not 20: $bytes"
  )

  def fromPublicKey(hashFunction: Array[Byte] => Array[Byte])(publicKey: BigInt): Address = {
    val hash = hashFunction(publicKey.toByteArray)
    new Address(ByteVector(hash, hash.size - 20, 20))
  }

  def fromBase64(base64: String): Either[String, Address] = for {
    bytes <- ByteVector.fromBase64Descriptive(base64)
    address <- Address(bytes)
  } yield address

  def fromHex(hexString: String): Either[String, Address] = for {
    bytes <- ByteVector.fromHexDescriptive(hexString)
    address <- Address(bytes)
  } yield address
}
