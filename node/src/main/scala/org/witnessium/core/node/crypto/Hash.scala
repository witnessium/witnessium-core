package org.witnessium.core
package node.crypto

import scodec.bits.ByteVector
import shapeless.tag.@@
import codec.byte.ByteEncoder
import datatype.{MerkleTrieNode, UInt256Bytes, UInt256Refine}
import model._

trait Hash[A] {
  def apply(a: A): Hash.Value[A]

  def contraMap[B](f: B => A): Hash[B] = (b: B) => Hash.Value[B](apply(f(b)))
}

object Hash {

  type Value[A] = UInt256Bytes @@ A

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private [crypto] def Value[A](uint256: UInt256Bytes): Value[A] = uint256.asInstanceOf[Value[A]]

  def apply[A](implicit h: Hash[A]): Hash[A] = h

  object ops {
    implicit class HashOps[A](val a: A) extends AnyVal {
      def toHash(implicit h: Hash[A]): UInt256Bytes = h(a)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def hash[A: ByteEncoder](a: A): Value[A] = {
    val bytes = ByteEncoder[A].encode(a)
    val hash = ByteVector.view(keccak256(bytes.toArray))
    Value[A](UInt256Refine.from(hash).toOption.get)
  }

  implicit val blockHeaderHash: Hash[BlockHeader] = hash[BlockHeader]
  implicit val blockHash: Hash[Block] = Hash[BlockHeader].contraMap(_.header)

  implicit val transactionHash: Hash[Transaction] = hash[Transaction]
  implicit def verifiableHash[A: Hash]: Hash[Verifiable[A]] = Hash[A].contraMap(_.value)
  implicit def signedHash[A: Hash]: Hash[Signed[A]] = Hash[A].contraMap(_.value)
  implicit def genesisHash[A: Hash]: Hash[Genesis[A]] = Hash[A].contraMap(_.value)

  implicit val merkleTrieNodeHash: Hash[MerkleTrieNode] = hash[MerkleTrieNode]
  implicit val merkleTrieNodeBranchHash: Hash[MerkleTrieNode.Branch] = Hash[MerkleTrieNode].contraMap(identity)
  implicit val merkleTrieNodeLeafHash: Hash[MerkleTrieNode.Leaf] = Hash[MerkleTrieNode].contraMap(identity)

  implicit val publicKeyHash: Hash[PublicKey] = hash[PublicKey]

  implicit val uint256bytesHash: Hash[UInt256Bytes] = hash
  implicit val hashListHash: Hash[List[UInt256Bytes]] = hash
  implicit val transactionHashListHash: Hash[List[Value[Transaction]]] = Hash[List[UInt256Bytes]].contraMap(identity)

  implicit val addressHash: Hash[Address] = hash

}
