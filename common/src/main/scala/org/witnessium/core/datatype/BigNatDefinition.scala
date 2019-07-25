package org.witnessium.core.datatype

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

trait BigNatDefinition {
  type BigNat = BigInt Refined NonNegative
}

