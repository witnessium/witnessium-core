package org.witnessium.core
package node
package repository
package interpreter

import eu.timepit.refined.refineMV
import eu.timepit.refined.numeric.NonNegative

import scodec.bits.ByteVector
import swaydb.serializers.Default._

import org.witnessium.core.codec.byte.ByteEncoder
import crypto.keccak256
import crypto.KeyPair
import datatype.UInt256Refine
import model.{Address, Signed, Transaction}

import utest._

object TransactionRepositoryInterpreterTest extends TestSuite {

  def newRepo: TransactionRepositoryInterpreter =
    new TransactionRepositoryInterpreter(swaydb.memory.zero.Map[Array[Byte], Array[Byte]]().get)

  val keyPair = KeyPair.generate()

  val targetAddress = Address(ByteVector.fromHex("0x0102030405060708091011121314151617181920").get).toOption.get
  val targetAmount = refineMV[NonNegative](BigInt(100))

  val transaction = Transaction(
    networkId = refineMV[NonNegative](BigInt(1)),
    inputs = Set.empty[Address],
    outputs = Set((targetAddress, targetAmount)),
  )

  val transactionHash = UInt256Refine.from{
    ByteVector.view(keccak256(ByteEncoder[Transaction].encode(transaction).toArray))
  }.toOption.get

  val signature = keyPair.sign(transactionHash.toArray).toOption.get

  val signedTransaction = Signed[Transaction](transaction, signature)

  val tests = Tests {
    test("get from empty repository") {
      val repo = newRepo
      (for {
        trEither <- repo.get(transactionHash)
        _ <- repo.close()
      } yield {
        assertMatch(trEither){ case Left(_) => }
      }).toFuture
    }

    test("put and get") {
      val repo = newRepo
      (for {
        _ <- repo.put(signedTransaction)
        trEither <- repo.get(transactionHash)
        _ <- repo.close()
      } yield {
        assertMatch(trEither){ case Right(tr) if tr === signedTransaction => }
      }).toFuture
    }

    test("removeWithHash") {
      val repo = newRepo
      (for {
        _ <- repo.put(signedTransaction)
        _ <- repo.removeWithHash(transactionHash)
        result <- repo.get(transactionHash)
        _ <- repo.close()
      } yield {
        assertMatch(result){ case Left(_) => }
      }).toFuture
    }

    test("remove") {
      val repo = newRepo
      (for {
        _ <- repo.put(signedTransaction)
        _ <- repo.remove(signedTransaction)
        result <- repo.get(transactionHash)
        _ <- repo.close()
      } yield {
        assertMatch(result){ case Left(_) => }
      }).toFuture
    }
  }
}
