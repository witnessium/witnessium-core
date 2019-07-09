package org.witnessium.core
package model

import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Interval

import datatype.UInt256BigInt

final case class Signature(v: Int Refined Signature.HeaderRange, r: UInt256BigInt, s: UInt256BigInt)

object Signature {
  type HeaderRange = Interval.Closed[W.`27`.T, W.`34`.T]
}
