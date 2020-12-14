package org.witnessium.core
package node
package endpoint

import cats.effect.Sync
import io.circe.generic.auto._
import io.circe.refined._
import io.finch._
import io.finch.circe._

import codec.circe._
import datatype.UInt256Bytes
import model.Transaction
import service.TransactionService

object HashEndpoint {

  def Post[F[_]:Sync](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, UInt256Bytes] = {

    import finch._

    post("hash" :: jsonBody[Transaction]) { (tx: Transaction)  =>
      Ok(TransactionService.hash(tx))
    }
  }
}
