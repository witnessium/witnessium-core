package org.witnessium.core.datatype

final case class Confidential[A](a: A) extends AnyVal {
  override def toString: String = "~~confidential~~"
}