package org.witnessium.core
package node
package service

import datatype.UInt256Bytes
import model.{Block, BlockHeader, NodeStatus, State}

trait PeerConnectionService[F[_]] extends GossipMessagePublisher[F] {
  def bestStateAndBlockHeader(localStatus: NodeStatus): F[Either[String, Option[(State, BlockHeader)]]]
  def block(blockHash: UInt256Bytes): F[Either[String, Block]]
  def stop(): F[Unit]
}
