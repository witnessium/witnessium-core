package org.witnessium.core
package node
package repository
package interpreter

import scala.concurrent.Future
import eu.timepit.refined.refineMV
import eu.timepit.refined.numeric.NonNegative
import scodec.bits.ByteVector
import swaydb.data.IO
import swaydb.serializers.Default._

import crypto.KeyPair
import model.{Address, Signed, Transaction}

import utest._

object TransactionRepositoryInterpreterTest extends TestSuite {

  val keyPair = KeyPair.generate()

  val targetAddress = Address(ByteVector.fromHex("0x0102030405060708091011121314151617181920").get).toOption.get
  val targetAmount = refineMV[NonNegative](BigInt(100))

  val transaction = Transaction(
    networkId = refineMV[NonNegative](BigInt(1)),
    inputs = Set.empty[Address],
    outputs = Set((targetAddress, targetAmount)),
  )

  val transactionHash = crypto.hash(transaction)

  val signature = keyPair.sign(transactionHash.toArray).toOption.get

  val signedTransaction = Signed[Transaction](transaction, signature)

  def withNewRepo[A](testBody: TransactionRepository[IO] => IO[A]): Future[A] = (for {
    db <- swaydb.memory.Map[Array[Byte], Array[Byte]]()
    newRepo = new TransactionRepositoryInterpreter(db)
    result <- testBody(newRepo)
  } yield result).toFuture

  val tests = Tests {
    test("get from empty repository") - withNewRepo { repo =>
      for {
        trEither <- repo.get(transactionHash)
      } yield {
        assertMatch(trEither){ case Left(_) => }
      }
    }

    test("put and get") - withNewRepo { repo =>
      for {
        _ <- repo.put(signedTransaction)
        trEither <- repo.get(transactionHash)
      } yield {
        assert(trEither === Right(signedTransaction))
      }
    }

    test("removeWithHash") - withNewRepo { repo =>
      for {
        _ <- repo.put(signedTransaction)
        _ <- repo.removeWithHash(transactionHash)
        result <- repo.get(transactionHash)
      } yield {
        assertMatch(result){ case Left(_) => }
      }
    }

    test("remove") - withNewRepo { repo =>
      for {
        _ <- repo.put(signedTransaction)
        _ <- repo.remove(signedTransaction)
        result <- repo.get(transactionHash)
      } yield {
        assertMatch(result){ case Left(_) => }
      }
    }
  }
}
