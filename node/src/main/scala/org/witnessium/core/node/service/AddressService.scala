package org.witnessium.core
package node
package service

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import crypto.MerkleTrie.{MerkleTrieState, NodeStore}
import datatype.UInt256Bytes
import model.{Address, Transaction}
import repository.{BlockRepository, StateRepository, TransactionRepository}
import StateRepository._

object AddressService{

  def unusedTxHashes[F[_]: Sync: BlockRepository: NodeStore](
    address: Address
  ): EitherT[F, String, List[UInt256Bytes]] = for {
    bestHeaderOption <- implicitly[BlockRepository[F]].bestHeader
    bestHeader <- EitherT.fromOption[F](bestHeaderOption, s"No best header in finding unused tx hashes: $address")
    all <- MerkleTrieState.fromRoot(bestHeader.stateRoot).getAll
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"$all state contents: $all")))
    txHashes <- MerkleTrieState.fromRoot(bestHeader.stateRoot).get(address)
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"$address utxo: $txHashes")))
  } yield txHashes

  def unusedTxs[F[_]: Sync: BlockRepository: NodeStore: TransactionRepository](
    address: Address
  ): EitherT[F, String, List[Transaction.Verifiable]] = for {
    txHashes <- unusedTxHashes[F](address)
    txs <- txHashes.traverse{ txHash =>
      for {
        txOption <- implicitly[TransactionRepository[F]].get(txHash)
        tx <- EitherT.fromOption[F](txOption, s"Transaction $txHash is not exist")
      } yield tx
    }
  } yield txs

  def balanceFromUnusedTxs(address: Address)(txs: List[Transaction.Verifiable]): BigInt = (for {
    tx <- txs
    (address1, amount) <- tx.value.outputs if address1 === address
  } yield amount.value).sum

  def balance[F[_]: Sync: BlockRepository: NodeStore: TransactionRepository](
    address: Address
  ): EitherT[F, String, BigInt] = unusedTxs[F](address) map balanceFromUnusedTxs(address)

  def balanceWithUnusedTxs[F[_]: Sync: BlockRepository: NodeStore: TransactionRepository](
    address: Address
  ): EitherT[F, String, (BigInt, List[Transaction.Verifiable])] = for {
    txs <- unusedTxs[F](address)
  } yield (balanceFromUnusedTxs(address)(txs), txs)

}
