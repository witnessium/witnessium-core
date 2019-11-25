package org.witnessium.core
package node
package service

import java.time.Instant
import scala.concurrent.duration._
import cats.Monad
import cats.data.EitherT
import cats.effect.{Sync, Timer}
import cats.implicits._
import eu.timepit.refined.refineV
import eu.timepit.refined.numeric.NonNegative

import crypto._
import crypto.MerkleTrie.MerkleTrieState
import crypto.KeyPair
import crypto.Hash.ops._
import datatype.{MerkleTrieNode, UInt256Bytes}
import model.{Address, Block, BlockHeader, Genesis, Signed, Transaction}
import model.api.{TransactionInfo, TransactionInfoBrief}
import repository.{BlockRepository, StateRepository, TransactionRepository}
import repository.StateRepository._
import store.HashStore

object TransactionService {

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def transactionToSenderAdddress(transaction: Transaction.Verifiable)(
    txHash: UInt256Bytes = transaction.toHash
  ): Option[Address] = transaction match {
    case Genesis(_) => None
    case Signed(sig, value@_) => sig.signedMessageHashToKey(txHash).map(Address.fromPublicKey(keccak256)).toOption
  }

  def transactionHashToTransactionInfo[F[_]: Monad: BlockRepository: TransactionRepository](
    txHash: UInt256Bytes
  ): EitherT[F, String, TransactionInfoBrief] = for {
    txOption <- implicitly[TransactionRepository[F]].get(txHash)
    tx <- EitherT.fromOption[F](txOption, s"Transacion $txHash not found")
    blockHashOption <- implicitly[BlockRepository[F]].findByTransaction(txHash)
    blockHash <-  EitherT.fromOption[F](blockHashOption, s"Block with tx $txHash not found")
    blockOption <- implicitly[BlockRepository[F]].get(blockHash)
    block <- EitherT.fromOption[F](blockOption, s"Block $blockHash not found")
  } yield TransactionInfoBrief(
    txHash = txHash,
    confirmedAt = block.header.timestamp,
    inputAddress = transactionToSenderAdddress(tx)(txHash),
    outputs = tx.value.outputs.toList,
  )

  def findByAddress[F[_]: Monad: BlockRepository: TransactionRepository](
    address: Address, offset: Int, limit: Int
  ): EitherT[F, String, List[TransactionInfoBrief]] = for {
    txHashes <- implicitly[TransactionRepository[F]].listByAddress(address, offset, limit)
    txInfos <- txHashes.traverse[EitherT[F, String, *], TransactionInfoBrief](transactionHashToTransactionInfo)
  } yield txInfos.sortBy(-_.confirmedAt.getEpochSecond())

  def get[F[_]: Monad: BlockRepository: TransactionRepository](
    transactionHash: UInt256Bytes
  ): EitherT[F, String, Option[TransactionInfo]] = {
    implicitly[TransactionRepository[F]].get(transactionHash).flatMap{ txOption =>
      txOption.traverse { tx => for {
        blockHashOption <- implicitly[BlockRepository[F]].findByTransaction(transactionHash)
        blockInfoOption <- blockHashOption.traverse(BlockService.blockHashToBlockInfo[F])
      } yield TransactionInfo(
        blockInfo = blockInfoOption,
        txHash = transactionHash,
        tx = tx,
      )}
    }
  }

  def addressFromSignedTransaction(transaction: Transaction.Signed): Either[String, Address] = for {
    (pubKey: BigInt) <- transaction.signature.signedMessageHashToKey(transaction.toHash)
  } yield Address.fromPublicKey(crypto.keccak256)(pubKey)

  def submit[F[_]: Timer: Sync: BlockRepository: TransactionRepository](
    transaction: Transaction.Signed,
    localKeyPair: KeyPair,
  )(implicit hashStore: HashStore[F, MerkleTrieNode]): EitherT[F, String, UInt256Bytes] = for {
    bestBlockHeaderOption <- implicitly[BlockRepository[F]].bestHeader
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"Best block header: $bestBlockHeaderOption")))
    bestBlockHeader <- EitherT.fromOption[F](bestBlockHeaderOption, "No best block header")
    txHash = transaction.toHash
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"generating new block with transactions: $txHash")))
    number <- EitherT.fromEither[F](refineV[NonNegative](bestBlockHeader.number.value + 1))
    now <- EitherT.right[String](Timer[F].clock.realTime(MILLISECONDS))
    fromAddress <- EitherT.fromEither[F](addressFromSignedTransaction(transaction))
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"From address: $fromAddress")))
    state <- MerkleTrieState.fromRoot(bestBlockHeader.stateRoot).put(fromAddress, transaction.value)
    stateRoot <- EitherT.fromOption[F](state.root, s"No root is found from $state")

    newBlockHeader = BlockHeader(
      number = number,
      parentHash = bestBlockHeader.toHash,
      stateRoot = stateRoot,
      transactionsRoot = crypto.hash(List(txHash)),
      timestamp = Instant.ofEpochMilli(now),
    )
    newBlockHash = newBlockHeader.toHash
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"next block header: $newBlockHeader")))
    signature <- EitherT.fromEither[F](localKeyPair.sign(newBlockHash.toArray))
    newBlock = Block(
      header = newBlockHeader,
      transactionHashes = Set(txHash),
      votes = Set(signature),
    )

    _ <- implicitly[BlockRepository[F]].put(newBlock)
    _ <- StateRepository.put[F](state)
    _ <- implicitly[TransactionRepository[F]].put(transaction)
  } yield txHash
}
