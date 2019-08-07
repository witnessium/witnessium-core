package org.witnessium.core.datatype

final case class Confidential[A](value: A) extends AnyVal {
  override def toString: String = "~~confidential~~"
}
