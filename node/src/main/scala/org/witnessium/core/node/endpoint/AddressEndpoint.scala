package org.witnessium.core
package node
package endpoint

import cats.effect.Async
import cats.implicits._
import io.finch._

import crypto.MerkleTrie.NodeStore
import model.Address
import model.api.{AddressInfo, AddressUtxoInfo}
import repository.{BlockRepository, TransactionRepository}
import service.AddressService

object AddressEndpoint {

  def GetUtxo[F[_]: Async: BlockRepository: NodeStore: TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, AddressUtxoInfo] = {
    import finch._

    get("address" :: "utxo" :: path[Address].withToString("{address}")) { (address: Address) =>
      AddressService.balanceWithUnusedTxhashes[F](address).value.map {
        case Right((balance, txHashes)) => Ok(AddressUtxoInfo(address, balance, txHashes))
        case Left(errorMsg) =>
          scribe.info(s"Get address UTXO of $address error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }

  def GetInfo[F[_]: Async: BlockRepository: NodeStore: TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, AddressInfo] = {
    import finch._

    get("address" :: path[Address].withToString("{address}")) { (address: Address) =>
      AddressService.getInfo[F](address).value.map {
        case Right(addressInfo) => Ok(addressInfo)
        case Left(errorMsg) =>
          scribe.info(s"Get address info of $address error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }
}
