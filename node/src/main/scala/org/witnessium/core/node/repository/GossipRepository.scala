package org.witnessium.core
package node
package repository

import datatype.UInt256Bytes
import model.{BlockHeader, Signature, Transaction}
import p2p.BloomFilter

trait GossipRepository[F[_]] {

  type BlockSuggestion = (BlockHeader, Set[UInt256Bytes])

  def blockSuggestions(): F[Either[String, Set[BlockSuggestion]]]

  def blockSuggestion(blockHash: UInt256Bytes): F[Either[String, Option[BlockSuggestion]]]

  def blockVotes(blockHash: UInt256Bytes): F[Either[String, Set[Signature]]]

  def newTransactions(bloomFilter: BloomFilter): F[Either[String, Set[Transaction.Signed]]]

  def newTransaction(transactionHash: UInt256Bytes): F[Either[String, Option[Transaction.Signed]]]

  def putNewBlockSuggestion(header: BlockHeader, transactionHashes: Set[UInt256Bytes]): F[Unit]

  def putNewTransaction(transaction: Transaction.Signed): F[Unit]

  def putNewBlockVote(blockHash: UInt256Bytes, signature: Signature): F[Unit]

  def finalizeBlock(blockhash: UInt256Bytes): F[Either[String, Unit]]

  def close(): F[Unit]

}