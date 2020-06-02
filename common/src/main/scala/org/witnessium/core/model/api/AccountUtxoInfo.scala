package org.witnessium.core
package model
package api

import datatype.UInt256Bytes

final case class AccountUtxoInfo(
  account: Account,
  balance: BigInt,
  utxoHashes: List[UInt256Bytes],
)
