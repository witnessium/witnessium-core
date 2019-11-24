package org.witnessium.core
package model.api

import java.time.Instant
import datatype.{BigNat, UInt256Bytes}

final case class BlockInfoBrief(
  blockNumber: BigNat,
  blockHash: UInt256Bytes,
  createdAt: Instant,
  numberOfTransaction: Int,
)
