package org.witnessium.core
package model
package api

import java.time.Instant
import datatype.{BigNat, UInt256Bytes}

final case class TransactionInfo(
  blockInfo: Option[TransactionInfo.BlockInfo],
  tranInfo: TransactionInfo.Summary,
  tran: TransactionInfo.Data,
)

object TransactionInfo {
  final case class BlockInfo(
    blockNumber: BigNat,
    blockHash: UInt256Bytes,
    timestamp: Instant,
    stateRoot: UInt256Bytes,
  )

  final case class Summary(
    tranHash: UInt256Bytes,
    totalValue: BigInt,
  )

  final case class Data(
    sendAddress: Option[Address],
    items: List[Item],
  )

  final case class Item(
    sendAddress: Option[UInt256Bytes],
    amt: Option[BigInt],
    receiveAccount: Option[Account],
    value: Option[BigNat],
  )
}
