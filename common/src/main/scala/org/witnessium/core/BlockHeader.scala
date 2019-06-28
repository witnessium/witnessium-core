package org.witnessium.core

import java.time.Instant
import UInt256Refine.UInt256Bytes

final case class BlockHeader(
  parentHash: UInt256Bytes,
  stateRoot: UInt256Bytes,
  transactionsRoot: UInt256Bytes,
  timestamp: Instant
)
