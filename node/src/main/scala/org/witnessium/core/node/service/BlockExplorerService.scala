package org.witnessium.core
package node
package service

import datatype.UInt256Bytes
import model.{Address, Block, Transaction}

trait BlockExplorerService[F[_]] {
  def transaction(transactionHash: UInt256Bytes): F[Either[String, Option[Transaction.Verifiable]]]

  def unused(address: Address): F[Either[String, Seq[Transaction.Verifiable]]]

  def block(blockHash: UInt256Bytes): F[Either[String, Option[Block]]]
}
