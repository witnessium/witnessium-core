package org.witnessium.core
package model

import java.time.Instant
import datatype.UInt256Bytes

final case class BlockHeader(
  number: BigNat,
  parentHash: UInt256Bytes,
  stateRoot: UInt256Bytes,
  transactionsRoot: UInt256Bytes,
  timestamp: Instant
)
