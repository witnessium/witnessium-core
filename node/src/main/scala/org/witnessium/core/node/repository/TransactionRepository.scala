package org.witnessium.core
package node.repository

import datatype.UInt256Bytes
import model.Transaction

trait TransactionRepository[F[_]] {

  def get(transactionHash: UInt256Bytes): F[Either[String, Transaction.Verifiable]]

  def put(signedTransaction: Transaction.Verifiable): F[Either[String, Unit]]

  def removeWithHash(transactionHash: UInt256Bytes): F[Unit]

  def remove(signedTransaction: Transaction.Verifiable): F[Unit]

  def close(): F[Unit]

}
