package org.witnessium.core.model
package api

final case class AddressUtxoInfo(
  address: Address,
  balance: BigInt,
  transactions: List[Transaction.Verifiable],
)
