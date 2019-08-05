package org.witnessium.core.model

sealed trait Verifiable[A] {
  def value: A
}

final case class Signed[A](value: A, signature: Signature) extends Verifiable[A]
final case class Genesis[A](value: A) extends Verifiable[A]
