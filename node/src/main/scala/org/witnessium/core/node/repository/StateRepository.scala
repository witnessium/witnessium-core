package org.witnessium.core
package node.repository

import datatype.UInt256Bytes
import model.Address

trait StateRepository[F[_]] {

  def contains(address: Address, transactionHash: UInt256Bytes): F[Boolean]

  def get(address: Address): F[Seq[UInt256Bytes]]

  def put(address: Address, transactionHash: UInt256Bytes): F[Unit]

  def remove(address: Address, transactionHash: UInt256Bytes): F[Unit]

  def close(): F[Unit]

}
