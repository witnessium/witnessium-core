package org.witnessium.core
package model
package api

import datatype.UInt256Bytes

final case class TransactionInfo(
  blockInfo: Option[BlockInfoBrief],
  txHash: UInt256Bytes,
  tx: Transaction.Verifiable,
)
