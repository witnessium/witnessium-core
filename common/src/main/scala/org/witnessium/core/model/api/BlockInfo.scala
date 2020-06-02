package org.witnessium.core
package model
package api

import java.time.Instant
import datatype.{BigNat, UInt256Bytes}

final case class BlockInfo(
  blockInfo: BlockInfo.Block,
  trans: List[BlockInfo.Transaction],
)

object BlockInfo {

  final case class Block(
    blockNumber: BigNat,
    blockHash: UInt256Bytes,
    createdAt: Instant,
    numberOfTransaction: Int,
    stateRoot: UInt256Bytes,
    parentHash: UInt256Bytes,
  )

  final case class Transaction(
    tranHash: UInt256Bytes,
    totalValue: BigInt,
    items: List[TransactionItem],
  )

  final case class TransactionItem(
    sendAccount: Option[Account],
    amt: Option[BigInt],
    receiveAccount: Account,
    value: BigNat,
  )
}
