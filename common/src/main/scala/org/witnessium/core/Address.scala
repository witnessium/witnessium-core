package org.witnessium.core

final case class Address(value: String) extends AnyVal

object Address {
  def fromPublicKey(publicKey: BigInt): Address = ???
}
