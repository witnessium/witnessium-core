package org.witnessium.core

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

package object model {
  type NetworkId = BigNat

  type BigNat = BigInt Refined NonNegative
}
