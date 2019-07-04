package org.witnessium.core.model

final case class Signed[A](value: A, signature: Signature)
