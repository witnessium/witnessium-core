package org.witnessium.core
package node.service

import model.Transaction

class TransactionService[F[_]] {
  def submit(transaction: Transaction): F[Either[String, UInt256Refine.UInt256Bytes]] = ???
}
