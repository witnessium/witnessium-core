package org.witnessium.core
package datatype

import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.Size
import eu.timepit.refined.numeric.{Divisible, Interval}
import scodec.bits.{BitVector, ByteVector}
import shapeless.Sized
import shapeless.nat._16

sealed trait MerkleTrieNode {
  def prefix: BitVector Refined MerkleTrieNode.PrefixCondition
}

object MerkleTrieNode {
  final case class Branch(prefix: BitVector Refined PrefixCondition, children: Children) extends MerkleTrieNode
  final case class Leaf(prefix: BitVector Refined PrefixCondition, value: ByteVector) extends MerkleTrieNode

  type Children = Vector[UInt256Bytes] Sized _16

  type PrefixCondition = Size[Interval.Closed[W.`0L`.T, W.`508L`.T] And Divisible[W.`4L`.T]]
}
