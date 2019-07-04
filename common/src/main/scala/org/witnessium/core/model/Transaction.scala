package org.witnessium.core
package model

import scala.collection.SortedSet

final case class Transaction(
  networkId: NetworkId,
  inputs: SortedSet[Address],
  outputs: SortedSet[(Address, UInt256Refine.UInt256BigInt)],
)

object Transaction {
  type Signed = model.Signed[Transaction]
}
