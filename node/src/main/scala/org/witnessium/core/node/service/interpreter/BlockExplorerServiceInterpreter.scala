package org.witnessium.core
package node
package service
package interpreter

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import swaydb.data.{IO => SwayIO}
import datatype.UInt256Bytes
import model.{Address, Block, Transaction}
import repository.{BlockRepository, GossipRepository, StateRepository, TransactionRepository}
import util.SwayIOCats._

class BlockExplorerServiceInterpreter(
  blockRepository: BlockRepository[SwayIO],
  gossipRepository: GossipRepository[SwayIO],
  stateRepository: StateRepository[SwayIO],
  transactionRepository: TransactionRepository[SwayIO],
) extends BlockExplorerService[IO] {

  override def transaction(transactionHash: UInt256Bytes): IO[Either[String, Option[Transaction.Verifiable]]] = (for{
    finalizedTransactionOption <- EitherT(transactionRepository.get(transactionHash))
    transactionOption <- finalizedTransactionOption.fold(EitherT{
      gossipRepository.newTransaction(transactionHash)
    })(t => EitherT.rightT(Some(t)))
  } yield transactionOption).value.toIO

  override def unused(address: Address): IO[Either[String, Seq[Transaction.Verifiable]]] = (for {
    transactionHashes <- EitherT(stateRepository.get(address))
    transactions <- transactionHashes.toList.traverse(transactionHash => for {
      transactionOption <- EitherT(transactionRepository.get(transactionHash))
      transaction <- EitherT.fromOption[SwayIO](transactionOption, s"Fail to find transaction $transactionHash")
    } yield transaction)
  } yield transactions).value.toIO

  override def block(blockHash: UInt256Bytes): IO[Either[String, Option[Block]]] = (for{
    blockHeaderOption <- EitherT(blockRepository.getHeader(blockHash))
    blockOption <- blockHeaderOption.fold(EitherT.pure[SwayIO, String](Option.empty[Block]))(blockHeader => for {
      transactionHashes <-EitherT(blockRepository.getTransactionHashes(blockHash))
      signatures <-EitherT(blockRepository.getSignatures(blockHash))
    } yield Some(Block(blockHeader, transactionHashes.toSet, signatures.toSet)))
  } yield blockOption).value.toIO
}
