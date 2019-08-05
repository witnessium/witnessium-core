package org.witnessium.core
package node.service

import cats.Monad
import cats.effect.IO
import cats.implicits._

class BlockchainCoreNodeService[F[_]: Monad](
  nodeInitializationService: NodeInitializationService[F],
  blockSuggentionService: BlockSuggestionService[F],
  transactionService: TransactionService[F],
  peerGossipPullingService: PeerGossipPullingService,
  nodeStateUpdateService: NodeStateUpdateService[IO],
){
  def start: F[Unit] = nodeInitializationService.initialize.map{ _ =>
    blockSuggentionService.listen(nodeStateUpdateService.onGossip)
    transactionService.listen(nodeStateUpdateService.onGossip)
    peerGossipPullingService.listen(nodeStateUpdateService.onGossip)
  }
}
