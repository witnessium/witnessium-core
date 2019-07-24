package org.witnessium.core
package model

import datatype.UInt256Bytes

final case class GossipMessage(
  blockSuggestions: Set[Block],
  blockVotes: Map[UInt256Bytes, Set[Signature]],
  newTransactions: Set[Transaction],
)
