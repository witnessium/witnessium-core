package org.witnessium.core
package node
package service
package interpreter

import cats.effect.IO
import datatype.UInt256Bytes
import model.{Address, Block, Transaction}

//import cats.data.EitherT
//import swaydb.data.{IO => SwayIO}
//import datatype.UInt256Bytes
//import model.{Block, GossipMessage, NetworkId, NodeStatus, State, Transaction}
//import repository.{BlockRepository, GossipRepository}
//import util.SwayIOCats._

class BlockExplorerServiceInterpreter() extends BlockExplorerService[IO] {

  override def transaction(transactionHash: UInt256Bytes): IO[Either[String, Option[Transaction.Verifiable]]] = ???

  override def unused(address: Address): IO[Either[String, Seq[Transaction.Verifiable]]] = ???

  override def block(blockHash: UInt256Bytes): IO[Either[String, Option[Block]]] = ???

}
