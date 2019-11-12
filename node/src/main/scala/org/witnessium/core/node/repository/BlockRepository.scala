package org.witnessium.core
package node.repository

import cats.data.EitherT
import datatype.UInt256Bytes
import model.{Block, BlockHeader, Signature}

trait BlockRepository[F[_]] {

  def getHeader(blockHash: UInt256Bytes): EitherT[F, String, Option[BlockHeader]]

  def bestHeader: EitherT[F, String, BlockHeader]

  def getTransactionHashes(blockHash: UInt256Bytes): EitherT[F, String, Seq[UInt256Bytes]]

  def getSignatures(blockHash: UInt256Bytes): EitherT[F, String, Seq[Signature]]

  def put(block: Block): EitherT[F, String, Unit]

}
