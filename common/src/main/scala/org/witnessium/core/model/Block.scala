package org.witnessium.core
package model

import datatype.UInt256Bytes

final case class Block(
  header: BlockHeader,
  transactionHashes: Set[UInt256Bytes],
  votes: Set[Signature],
)
