package org.witnessium.core
package model

import scala.collection.SortedSet

final case class Block(
  header: BlockHeader,
  transactionHashes: SortedSet[UInt256Refine.UInt256Bytes],
  signatures: SortedSet[Signature],
)
