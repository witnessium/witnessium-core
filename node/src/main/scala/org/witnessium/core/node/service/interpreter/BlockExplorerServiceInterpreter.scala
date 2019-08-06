package org.witnessium.core
package node
package service
package interpreter

import cats.data.EitherT
import cats.effect.IO
import swaydb.data.{IO => SwayIO}
import datatype.UInt256Bytes
import model.{Address, Block, Transaction}
import repository.{GossipRepository, TransactionRepository}
import util.SwayIOCats._

class BlockExplorerServiceInterpreter(
  gossipRepository: GossipRepository[SwayIO],
  transactionRepository: TransactionRepository[SwayIO],
) extends BlockExplorerService[IO] {

  override def transaction(transactionHash: UInt256Bytes): IO[Either[String, Option[Transaction.Verifiable]]] = (for{
    finalizedTransactionOption <- EitherT(transactionRepository.get(transactionHash))
    transactionOption <- finalizedTransactionOption.fold(EitherT{
      gossipRepository.newTransaction(transactionHash)
    })(t => EitherT.rightT(Some(t)))
  } yield transactionOption).value.toIO

  override def unused(address: Address): IO[Either[String, Seq[Transaction.Verifiable]]] = ???

  override def block(blockHash: UInt256Bytes): IO[Either[String, Option[Block]]] = ???

}
