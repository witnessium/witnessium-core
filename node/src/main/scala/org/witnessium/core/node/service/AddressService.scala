package org.witnessium.core
package node
package service

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import crypto.MerkleTrie.{MerkleTrieState, NodeStore}
import datatype.UInt256Bytes
import model.{Address, Transaction}
import model.api.{AddressInfo, TransactionInfoBrief}
import repository.{BlockRepository, StateRepository, TransactionRepository}
import StateRepository._

object AddressService{

  def unusedTxHashes[F[_]: Sync: BlockRepository: NodeStore](
    address: Address
  ): EitherT[F, String, List[UInt256Bytes]] = for {
    bestHeaderOption <- implicitly[BlockRepository[F]].bestHeader
    bestHeader <- EitherT.fromOption[F](bestHeaderOption, s"No best header in finding unused tx hashes: $address")
    all <- MerkleTrieState.fromRoot(bestHeader.stateRoot).getAll
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"=== all state contents ===")))
    _ <- EitherT.right(Sync[F].pure(all.foreach{ (content) => scribe.info(s"$content") }))
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"==========================")))
    txHashes <- MerkleTrieState.fromRoot(bestHeader.stateRoot).get(address)
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"$address utxo: $txHashes")))
  } yield txHashes

  def unusedTxs[F[_]: Sync: BlockRepository: NodeStore: TransactionRepository](
    address: Address
  ): EitherT[F, String, (List[Transaction.Verifiable], List[UInt256Bytes])] = for {
    txHashes <- unusedTxHashes[F](address)
    txs <- txHashes.traverse{ txHash =>
      for {
        txOption <- implicitly[TransactionRepository[F]].get(txHash)
        tx <- EitherT.fromOption[F](txOption, s"Transaction $txHash is not exist")
      } yield tx
    }
  } yield (txs, txHashes)

  def balanceFromUnusedTxs(address: Address)(txs: List[Transaction.Verifiable]): BigInt = (for {
    tx <- txs
    (address1, amount) <- tx.value.outputs if address1 === address
  } yield amount.value).sum

  def balance[F[_]: Sync: BlockRepository: NodeStore: TransactionRepository](
    address: Address
  ): EitherT[F, String, BigInt] = unusedTxs[F](address) map(_._1) map balanceFromUnusedTxs(address)

  def balanceWithUnusedTxhashes[F[_]: Sync: BlockRepository: NodeStore: TransactionRepository](
    address: Address
  ): EitherT[F, String, (BigInt, List[UInt256Bytes])] = for {
    (txs, txHashes) <- unusedTxs[F](address)
  } yield (balanceFromUnusedTxs(address)(txs), txHashes)

  def txInfoToTras(currentAddress: Address)(txInfo: TransactionInfoBrief): AddressInfo.Transaction = {

    def addMyAddress(items: List[AddressInfo.Item]): List[AddressInfo.Item] = items match {
      case Nil => Nil
      case x :: xs => x.copy(myAddress = Some(currentAddress)) :: xs
    }

    if (txInfo.inputAddress === Option(currentAddress)) {
      //sender
      val items = txInfo.outputs.map{ case (toAddress, amount) =>
        AddressInfo.Item(
          myAddress = None,
          receiveAddress = None,
          sendAddress = Some(toAddress),
          value = amount,
        )
      }

      AddressInfo.Transaction(
        `type` = "sender",
        tranHash = txInfo.txHash,
        timestamp = txInfo.confirmedAt,
        items = addMyAddress(items),
      )
    } else {
      //receiver
      val items = txInfo.outputs.map{ case (toAddress, amount) =>
        AddressInfo.Item(
          myAddress = None,
          receiveAddress = Some(toAddress),
          sendAddress = None,
          value = amount,
        )
      }

      AddressInfo.Transaction(
        `type` = "receiver",
        tranHash = txInfo.txHash,
        timestamp = txInfo.confirmedAt,
        items = addMyAddress(items),
      )
    }
  }

  def getInfo[F[_]: Sync: BlockRepository: NodeStore: TransactionRepository](
    address: Address
  ): EitherT[F, String, AddressInfo] = for {
    balanceValue <- balance[F](address)
    txinfos <- TransactionService.findByAddress[F](address, 0, Int.MaxValue)
  } yield AddressInfo(
    accountInfo = AddressInfo.Accoount(balance = balanceValue),
    trans = txinfos map txInfoToTras(address),
  )
}
