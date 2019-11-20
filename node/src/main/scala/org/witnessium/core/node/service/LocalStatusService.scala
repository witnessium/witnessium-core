package org.witnessium.core
package node
package service

import cats.Functor
import eu.timepit.refined.refineMV
import eu.timepit.refined.numeric.NonNegative
import cats.data.EitherT
import datatype.UInt256Bytes
import model.{NetworkId, NodeStatus}
import repository.BlockRepository

object LocalStatusService {

  def status[F[_]: Functor: BlockRepository](
    networkId: NetworkId,
    genesisHash: UInt256Bytes,
  ): EitherT[F, String, NodeStatus] = for {
    bestBlockHeader <- implicitly[BlockRepository[F]].bestHeader
  } yield NodeStatus(
    networkId = networkId,
    genesisHash = genesisHash,
    bestHash = bestBlockHeader.fold(genesisHash)(crypto.hash),
    number = bestBlockHeader.fold(refineMV[NonNegative](BigInt(0)))(_.number),
  )
}
