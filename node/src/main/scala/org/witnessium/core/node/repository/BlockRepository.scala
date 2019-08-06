package org.witnessium.core
package node.repository

import datatype.UInt256Bytes
import model.{Block, BlockHeader, Signature}

trait BlockRepository[F[_]] {

  def getHeader(blockHash: UInt256Bytes): F[Either[String, BlockHeader]]

  def bestHeader: F[Either[String, BlockHeader]]

  def getTransactionHashes(blockHash: UInt256Bytes): F[Either[String, Seq[UInt256Bytes]]]

  def getSignatures(blockHash: UInt256Bytes): F[Either[String, Seq[Signature]]]

  def put(block: Block): F[Unit]

  def close(): F[Unit]

}
