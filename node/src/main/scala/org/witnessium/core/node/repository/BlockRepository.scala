package org.witnessium.core
package node.repository

import cats.data.EitherT
import model.{Block, BlockHeader}

trait BlockRepository[F[_]] extends HashStore[F, Block] {

  def bestHeader: EitherT[F, String, BlockHeader]

}
