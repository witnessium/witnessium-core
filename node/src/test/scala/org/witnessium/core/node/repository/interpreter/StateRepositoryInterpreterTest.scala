package org.witnessium.core
package node
package repository
package interpreter

import scala.concurrent.{ExecutionContext, Future}

import scodec.bits.ByteVector
import swaydb.serializers.Default._

import datatype.{UInt256Bytes, UInt256Refine}
import model.Address

import utest._

object StateRepositoryInterpreterTest extends TestSuite {

  implicit val ec = ExecutionContext.global

  def newRepo: StateRepository[Future] =
    new StateRepositoryInterpreter(swaydb.memory.zero.Map[Array[Byte], Array[Byte]]().get)

  val targetAddress = Address(ByteVector.fromHex("0x0102030405060708091011121314151617181920").get).toOption.get

  def transactionHash(hex: String): UInt256Bytes = (for {
    bytes <- ByteVector.fromHexDescriptive(hex)
    refined <- UInt256Refine.from(bytes)
  } yield refined).toOption.get

  val transactionHash1 = transactionHash("0x0102030405060708091011121314151617181920212223242526272829303132")
  val transactionHash2 = transactionHash("0x1102030405060708091011121314151617181920212223242526272829303132")

  val tests = Tests {
    test("contains from empty repository") {
      val repo = newRepo
      repo.contains(targetAddress, transactionHash1).map { result =>
        assert(result === false)
      }
    }

    test("put and contains") {
      val repo = newRepo
      for {
        _ <- repo.put(targetAddress, transactionHash1)
        result <- repo.contains(targetAddress, transactionHash1)
      } yield {
        assert(result === true)
      }
    }

    test("put and get") {
      val repo = newRepo
      for {
        _ <- repo.put(targetAddress, transactionHash1)
        _ <- repo.put(targetAddress, transactionHash2)
        result <- repo.get(targetAddress)
      } yield {
        assert(result === Seq(transactionHash1, transactionHash2))
      }
    }

    test("remove") {
      val repo = newRepo
      for {
        _ <- repo.put(targetAddress, transactionHash1)
        _ <- repo.put(targetAddress, transactionHash2)
        _ <- repo.remove(targetAddress, transactionHash1)
        result <- repo.get(targetAddress)
      } yield {
        assert(result === Seq(transactionHash2))
      }
    }
  }
}

