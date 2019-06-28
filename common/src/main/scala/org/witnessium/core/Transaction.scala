package org.witnessium.core

final case class Transaction(
  networkId: NetworkId,
  inputs: Seq[Address],
  outputs: Seq[Address],
  amount: UInt256Refine.UInt256BigInt,
)
