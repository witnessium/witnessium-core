package org.witnessium.core
package model

import datatype.BigNat

final case class MultiSig(
  weights: Map[Address, BigNat],
  threshold: BigNat,
)
