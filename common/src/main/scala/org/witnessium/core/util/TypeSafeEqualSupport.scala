package org.witnessium.core.util

trait TypeSafeEqualSupport {

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  implicit final class TypeSafeEqualOps[A](self: A) {
    def ===(other: A): Boolean = self == other
    def =/=(other: A): Boolean = self != other
  }

}
