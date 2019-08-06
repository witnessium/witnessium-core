package org.witnessium.core
package node
package endpoint

import cats.effect.IO
import io.finch._
import io.finch.catsEffect._

import model.{Address, Transaction}
import service.BlockExplorerService

class AddressEndpoint(blockExplorerService: BlockExplorerService[IO]) {

  val Get: Endpoint[IO, Seq[Transaction.Verifiable]] = get("address" ::
    path[Address].withToString("address")
  ) { (address: Address) =>
    blockExplorerService.unused(address).map {
      case Right(transactions) => Ok(transactions)
      case Left(errorMsg) =>
        scribe.info(s"Get address $address error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }
}
