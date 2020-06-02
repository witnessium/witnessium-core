package org.witnessium.core
package model
package api

import java.time.Instant
import datatype.{BigNat, UInt256Bytes}

final case class AccountInfo(
  accountInfo: AccountInfo.Balance,
  trans: List[AccountInfo.Transaction],
)

object AccountInfo {

  final case class Balance(balance: BigInt)

  final case class Transaction(
    `type`: String,
    tranHash: UInt256Bytes,
    timestamp: Instant,
    items: List[Item],
  )

  final case class Item(
    myAccount: Option[Account],
    receiveAccount: Option[Account],
    sendAccount: Option[Account],
    value: BigNat,
  )
}
