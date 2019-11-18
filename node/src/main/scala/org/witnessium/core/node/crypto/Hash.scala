package org.witnessium.core
package node.crypto

import datatype.{MerkleTrieNode, UInt256Bytes}
import model._

trait Hash[A] {
  def apply(a: A): UInt256Bytes

  def contraMap[B](f: B => A): Hash[B] = (b: B) => apply(f(b))
}

object Hash {

  def apply[A](implicit h: Hash[A]): Hash[A] = h

  object ops {
    implicit class HashOps[A](val a: A) extends AnyVal {
      def toHash(implicit h: Hash[A]): UInt256Bytes = h(a)
    }
  }

  implicit val blockHeaderHash: Hash[BlockHeader] = hash[BlockHeader]
  implicit val blockHash: Hash[Block] = Hash[BlockHeader].contraMap(_.header)

  implicit val transactionHash: Hash[Transaction] = hash[Transaction]
  implicit def verifiableHash[A: Hash]: Hash[Verifiable[A]] = Hash[A].contraMap(_.value)
  implicit def signedHash[A: Hash]: Hash[Signed[A]] = Hash[A].contraMap(_.value)
  implicit def genesisHash[A: Hash]: Hash[Genesis[A]] = Hash[A].contraMap(_.value)

  implicit val merkleTrieNodeHash: Hash[MerkleTrieNode] = hash[MerkleTrieNode]

}
