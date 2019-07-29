package org.witnessium.core
package model

import datatype.UInt256Bytes

final case class GossipMessage(
  blockSuggestions: Set[(BlockHeader, Set[UInt256Bytes])],
  blockVotes: Map[UInt256Bytes, Set[Signature]],
  newTransactions: Set[Transaction],
)
