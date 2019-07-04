package org.witnessium.core.model

import scodec.bits.ByteVector

final case class Address private[model](bytes: ByteVector) extends AnyVal {
  override def toString: String = bytes.toBase64
}

object Address {
  def fromPublicKey(hashFunction: Array[Byte] => Array[Byte])(publicKey: BigInt): Address = {
    val hash = hashFunction(publicKey.toByteArray)
    Address(ByteVector(hash, hash.size - 20, 20))
  }

  def fromBase64(base64: String): Either[String, Address] = ByteVector.fromBase64Descriptive(base64).map(Address(_))
}
