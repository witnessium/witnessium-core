package org.witnessium.core
package node
package endpoint

import cats.effect.IO
import io.finch._
import io.finch.catsEffect._

import crypto.MerkleTrie.NodeStore
import model.Address
import model.api.{AddressInfo, AddressUtxoInfo}
import repository.{BlockRepository, TransactionRepository}
import service.AddressService

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class AddressEndpoint()(implicit
  blockRepository: BlockRepository[IO],
  nodeStore: NodeStore[IO],
  transactionRepository: TransactionRepository[IO],
) {

  val GetUTXO: Endpoint[IO, AddressUtxoInfo] = get("address" :: "utxo" ::
    path[Address].withToString("{address}")
  ) { (address: Address) =>
    AddressService.balanceWithUnusedTxs[IO](address).value.map {
      case Right((balance, txs)) => Ok(AddressUtxoInfo(address, balance, txs))
      case Left(errorMsg) =>
        scribe.info(s"Get address UTXO of $address error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }

  val GetInfo: Endpoint[IO, AddressInfo] = get("address" ::
    path[Address].withToString("{address}")
  ) { (address: Address) =>
    AddressService.getInfo[IO](address).value.map {
      case Right(addressInfo) => Ok(addressInfo)
      case Left(errorMsg) =>
        scribe.info(s"Get address info of $address error response: $errorMsg")
        InternalServerError(new Exception(errorMsg))
    }
  }
}
