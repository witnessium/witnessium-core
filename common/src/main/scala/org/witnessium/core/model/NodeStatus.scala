package org.witnessium.core
package model

import datatype.{BigNat, UInt256Bytes}

final case class NodeStatus(
  networkId: NetworkId,
  genesisHash: UInt256Bytes,
  bestHash: UInt256Bytes,
  number: BigNat,
)
