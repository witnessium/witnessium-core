package org.witnessium.core
package node
package service

import crypto._
import crypto.Hash.ops._
import datatype.UInt256Bytes
import model.{Address, Genesis, Signed, Transaction}

object ServiceUtil {
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def transactionToSenderAddress(transaction: Transaction.Verifiable)(
    txHash: UInt256Bytes = transaction.toHash
  ): Option[Address] = transaction match {
    case Genesis(_) => None
    case Signed(sig, value@_) => sig.signedMessageHashToKey(txHash).map(Address.fromPublicKey(keccak256)).toOption
  }
}
