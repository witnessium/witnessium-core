package org.witnessium.core
package model

import datatype.BigNat

final case class Transaction(
  networkId: NetworkId,
  inputs: Set[Address],
  outputs: Set[(Address, BigNat)],
)

object Transaction {
  type Signed = model.Signed[Transaction]
}
