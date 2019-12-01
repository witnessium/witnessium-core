package org.witnessium.core
package model
package api

import java.time.Instant
import datatype.{BigNat, UInt256Bytes}

final case class AddressInfo(
  accountInfo: AddressInfo.Accoount,
  trans: List[AddressInfo.Transaction],
)

object AddressInfo {

  final case class Accoount(balance: BigInt)

  final case class Transaction(
    `type`: String,
    tranHash: UInt256Bytes,
    timestamp: Instant,
    items: List[Item],
  )

  final case class Item(
    myAddress: Option[Address],
    receiveAddress: Option[Address],
    sendAddress: Option[Address],
    value: BigNat,
  )
}
