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
import datatype.{BigNat, MerkleTrieNode, UInt256Bytes}
import model.{Address, Block, BlockHeader, Transaction}
import model.api.{TransactionInfo, TransactionInfoBrief}
import repository.{BlockRepository, StateRepository, TransactionRepository}
import repository.StateRepository._
import store.HashStore

object TransactionService {

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
    inputAddress = ServiceUtil.transactionToSenderAddress(tx)(txHash),
    outputs = tx.value.outputs.toList,
  )

  def findByAddress[F[_]: Monad: BlockRepository: TransactionRepository](
    address: Address, offset: Int, limit: Int
  ): EitherT[F, String, List[TransactionInfoBrief]] = for {
    txHashes <- implicitly[TransactionRepository[F]].listByAddress(address, offset, limit)
    txInfos <- txHashes.traverse[EitherT[F, String, *], TransactionInfoBrief](transactionHashToTransactionInfo)
  } yield txInfos.sortBy(-_.confirmedAt.getEpochSecond())

  def getInfo[F[_]: Monad: BlockRepository: TransactionRepository](
    transactionHash: UInt256Bytes
  ): EitherT[F, String, Option[TransactionInfo]] = {

    def toTxInfoData(
      inputUtxos: List[(UInt256Bytes, BigInt)],
      outputs: List[(Address, BigNat)],
    ): List[TransactionInfo.Item] = {
      val input1 = inputUtxos.map{
        case (hash, amount) => (Some(hash), Some(amount))
      } ::: List.fill(outputs.size - inputUtxos.size max 0)((None, None))
      val output1 = outputs.map {
        case (address, amount) => (Some(address), Some(amount))
      } ::: List.fill(inputUtxos.size - outputs.size max 0)((None, None))
      input1 zip output1 map { case ((inHash, inAmount), (outAddress, outAmount)) =>
        TransactionInfo.Item(inHash, inAmount, outAddress, outAmount)
      }
    }

    implicitly[TransactionRepository[F]].get(transactionHash).flatMap{ txOption =>
      txOption.traverse(tx => for {
        blockHashOption <- implicitly[BlockRepository[F]].findByTransaction(transactionHash)
        blockInfoOption <- blockHashOption.traverse(blockHash => for {
          blockOption <- implicitly[BlockRepository[F]].get(blockHash)
          block <- EitherT.fromOption[F](blockOption, s"Block $blockHash not found")
        } yield TransactionInfo.BlockInfo(
          blockNumber = block.header.number,
          blockHash = blockHash,
          timestamp = block.header.timestamp,
          stateRoot = block.header.stateRoot,
        ))
        sendAddressOption = ServiceUtil.transactionToSenderAddress(tx)(transactionHash)
        inputUTXOs <- tx.value.inputs.toList.traverse{ inputTxHash =>
          for {
            inputTxOption <- implicitly[TransactionRepository[F]].get(inputTxHash)
            inputTx <- EitherT.fromOption[F](inputTxOption, s"Transacion $inputTxHash not found")
          } yield (
            inputTxHash,
            inputTx.value.outputs.filter{ output =>
              Option(output._1) === sendAddressOption
            }.map(_._2.value).sum
          )
        }
      } yield TransactionInfo(
        blockInfo = blockInfoOption,
        tranInfo = TransactionInfo.Summary(
          tranHash = transactionHash,
          totalValue = tx.value.outputs.map(_._2.value).sum
        ),
        tran = TransactionInfo.Data(
          sendAddress = sendAddressOption,
          items = toTxInfoData(inputUTXOs, tx.value.outputs.toList),
        )
      ))
    }
  }

  def get[F[_]: TransactionRepository](
    transactionHash: UInt256Bytes
  ): EitherT[F, String, Option[Transaction.Verifiable]] = {
    implicitly[TransactionRepository[F]].get(transactionHash)
  }

  def addressFromSignedTransaction(transaction: Transaction.Signed): Either[String, Address] = for {
    pubKey <- transaction.signature.signedMessageHashToKey(transaction.toHash)
  } yield Address.fromPublicKeyHash(pubKey.toHash)

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
