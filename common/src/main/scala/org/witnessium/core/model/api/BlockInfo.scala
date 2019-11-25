package org.witnessium.core
package model.api

import java.time.Instant
import datatype.{BigNat, UInt256Bytes}

final case class BlockInfo(
  blockNumber: BigNat,
  blockHash: UInt256Bytes,
  createdAt: Instant,
  numberOfTransaction: Int,
  stateRoot: UInt256Bytes,
  parentHash: UInt256Bytes,
)
