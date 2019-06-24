package org.witnessium

package object core {
  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  implicit final class TypeSafeEqualOps[A](self: A) {
    def ===(other: A): Boolean = self == other
  }
}
