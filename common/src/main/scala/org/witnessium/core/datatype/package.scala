package org.witnessium.core

package object datatype {
  type UInt256Refined[A] = UInt256Refine.UInt256Refined[A]
  type UInt256Bytes = UInt256Refine.UInt256Bytes
  type UInt256BigInt = UInt256Refine.UInt256BigInt
}
