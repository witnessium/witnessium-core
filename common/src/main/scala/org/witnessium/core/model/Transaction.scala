package org.witnessium.core
package model

import datatype.{BigNat, UInt256Bytes}

final case class Transaction(
  networkId: NetworkId,
  inputs: Set[UInt256Bytes],
  outputs: Set[(Address, BigNat)],
  ticketData: Option[TicketData],
)

object Transaction {
  type Verifiable = model.Verifiable[Transaction]
  type Signed = model.Signed[Transaction]
}
