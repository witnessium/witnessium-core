package org.witnessium.core
package model
package api

import datatype.UInt256Bytes

final case class AddressUtxoInfo(
  address: Address,
  balance: BigInt,
  utxoHashes: List[UInt256Bytes],
)
