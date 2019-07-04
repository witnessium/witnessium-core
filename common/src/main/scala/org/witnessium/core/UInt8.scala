package org.witnessium.core

final case class UInt8(value: Byte) extends AnyVal
object UInt8 {
  def from(int: Int): Either[String, UInt8] = {
    if (int < 0) Left[String, UInt8](s"UInt8 must be non-negative: $int")
    else if (int > 256) Left[String, UInt8](s"Too big to become UInt8: $int")
    else Right[String, UInt8](UInt8(int.toByte))
  }
}
