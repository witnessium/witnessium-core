package org.witnessium.core
package model
package api

import java.time.Instant
import datatype.{BigNat, UInt256Bytes}

final case class TransactionInfoBrief(
  txHash: UInt256Bytes,
  blockNumber: BigNat,
  confirmedAt: Instant,
  inputAddress: Option[Address],
  outputs: List[(Address, BigNat)],
)
