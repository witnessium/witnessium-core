package org.witnessium.core.model
package api

final case class AddressInfo(
  address: Address,
  balance: BigInt,
  transactions: List[Transaction.Verifiable],
)
