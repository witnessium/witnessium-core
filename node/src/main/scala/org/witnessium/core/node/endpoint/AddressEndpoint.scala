package org.witnessium.core
package node
package endpoint

import cats.effect.IO
import io.finch._
import io.finch.catsEffect._

import crypto.MerkleTrie.NodeStore
import model.Address
import model.api.AddressInfo
import repository.{BlockRepository, TransactionRepository}
import service.AddressService

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class AddressEndpoint()(implicit
  blockRepository: BlockRepository[IO],
  nodeStore: NodeStore[IO],
  transactionRepository: TransactionRepository[IO],
) {

  val Get: Endpoint[IO, AddressInfo] = get("address" ::
    path[Address].withToString("{address}")
  ) { (address: Address) =>
    AddressService.balanceWithUnusedTxs[IO](address).value.map {
      case Right((balance, txs)) => Ok(AddressInfo(address, balance, txs))
      case Left(errorMsg) =>
        scribe.info(s"Get address $address error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }
}
