package org.witnessium.core
package node
package repository
package interpreter

import scodec.bits.ByteVector
import swaydb.data.IO
import swaydb.serializers.Default._

import datatype.{UInt256Bytes, UInt256Refine}
import model.Address

import utest._

object StateRepositoryInterpreterTest extends TestSuite {

  def newRepo: StateRepository[IO] =
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
      (for {
        result <- repo.contains(targetAddress, transactionHash1)
        _ <- repo.close()
      } yield {
        assert(result === false)
      }).toFuture
    }

    test("put and contains") {
      val repo = newRepo
      (for {
        _ <- repo.put(targetAddress, transactionHash1)
        result <- repo.contains(targetAddress, transactionHash1)
        _ <- repo.close()
      } yield {
        assert(result === true)
      }).toFuture
    }

    test("put and get") {
      val repo = newRepo
      (for {
        _ <- repo.put(targetAddress, transactionHash1)
        _ <- repo.put(targetAddress, transactionHash2)
        result <- repo.get(targetAddress)
        _ <- repo.close()
      } yield {
        assert(result === Seq(transactionHash1, transactionHash2))
      }).toFuture
    }

    test("remove") {
      val repo = newRepo
      (for {
        _ <- repo.put(targetAddress, transactionHash1)
        _ <- repo.put(targetAddress, transactionHash2)
        _ <- repo.remove(targetAddress, transactionHash1)
        result <- repo.get(targetAddress)
        _ <- repo.close()
      } yield {
        assert(result === Seq(transactionHash2))
      }).toFuture
    }
  }
}

