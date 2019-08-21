package org.witnessium.core
package node
package endpoint

import cats.effect.IO
import eu.timepit.refined.refineMV
import eu.timepit.refined.numeric.NonNegative
import io.finch.Input
import scodec.bits.ByteVector

import datatype.UInt256Bytes
import model.{Address, Block, Genesis, GossipMessage, Transaction}
import service.{BlockExplorerService, TransactionService}

import utest._
import org.witnessium.core.model.ModelArbitrary

object TransactionEndpointTest extends TestSuite with ModelArbitrary  {

  val targetAddress = Address(ByteVector.fromHex("0x0102030405060708091011121314151617181920").get).toOption.get
  val targetAmount = refineMV[NonNegative](BigInt(100))

  val tx = Genesis(Transaction(
    networkId = refineMV[NonNegative](BigInt(1)),
    inputs = Set.empty,
    outputs = Set((targetAddress, targetAmount)),
  ))

  val hash = crypto.hash(tx)

  val transactionService = new TransactionService[IO]{
    def gossipListener: GossipMessage => IO[Unit] = ???
    def submit(transaction: Transaction.Signed): IO[UInt256Bytes] = ???
  }
  val blockExplorerService = new BlockExplorerService[IO]{

    def transaction(transactionHash: UInt256Bytes): IO[Either[String, Option[Transaction.Verifiable]]] = {
      if (transactionHash === hash) IO.pure(Right(Some(tx))) else IO.pure(Right(None))
    }

    def unused(address: Address): IO[Either[String, Seq[Transaction.Verifiable]]] = ???

    def block(blockHash: UInt256Bytes): IO[Either[String, Option[Block]]] = ???
  }

  val endpoint = new TransactionEndpoint(transactionService, blockExplorerService)

  val tests = Tests {
    test("define UInt256Bytes path"){
      println(s"===> hash: ${hash.toHex}")
      val e = io.finch.catsEffect.path[UInt256Bytes]
      val result = e(Input.get(s"/${hash.toHex}"))
      println(s"===> $result")
      assert(result.isMatched === true)
    }

    test("define GET /transaction/{transactionHash}"){
      println(s"===> hash: ${hash.toHex}")
      val input = Input.get(s"/transaction/${hash.toHex}")
      val result = endpoint.Get(input)
      println(s"===> $result")
      assert(result.isMatched === true)
    }
  }
}
