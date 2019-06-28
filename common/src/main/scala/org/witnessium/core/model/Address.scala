package org.witnessium.core.model

final case class Address(value: String) extends AnyVal

object Address {
  def fromPublicKey(publicKey: BigInt): Address = ???
}
