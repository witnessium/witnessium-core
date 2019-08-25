package org.witnessium.core
package node.repository

import datatype.{MerkleTrieNode, UInt256Bytes}
import model.Address
import org.witnessium.core.datatype.MerkleTrieNode

trait StateRepository[F[_]] {

  def getMerkleTrieNode(merkleRoot: UInt256Bytes): F[Either[String, Option[MerkleTrieNode]]]

  def contains(address: Address, transactionHash: UInt256Bytes): F[Boolean]

  def get(address: Address): F[Either[String, Seq[UInt256Bytes]]]

  def put(address: Address, transactionHash: UInt256Bytes): F[Unit]

  def remove(address: Address, transactionHash: UInt256Bytes): F[Unit]

  def close(): F[Unit]

}
