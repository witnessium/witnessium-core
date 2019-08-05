package org.witnessium.core
package node.service

import cats.Monad
import cats.implicits._

class BlockchainCoreNodeService[F[_]: Monad](
  nodeInitializationService: NodeInitializationService[F],
  blockSuggentionService: BlockSuggestionService[F],
  peerConnectionService: PeerConnectionService[F],
  transactionService: TransactionService[F],
  nodeStateUpdateService: NodeStateUpdateService[F],
){
  def start: F[Unit] = nodeInitializationService.initialize.map{ _ =>
    blockSuggentionService.listen(nodeStateUpdateService.onGossip)
    peerConnectionService.listen(nodeStateUpdateService.onGossip)
    transactionService.listen(nodeStateUpdateService.onGossip)
  }
}
