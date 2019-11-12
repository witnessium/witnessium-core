package org.witnessium.core
package node
package repository
package interpreter

import scala.concurrent.Future
import swaydb.data.IO
import swaydb.serializers.Default._

import model.Block

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.rng.Seed
import org.witnessium.core.model.ModelArbitrary
import utest._

object BlockRepositoryInterpreterTest extends TestSuite with ModelArbitrary {

  val block = Arbitrary.arbitrary[Block].pureApply(Gen.Parameters.default, Seed.random())

  val blockHash = crypto.hash(block.header)

  def withNewRepo[A](testBody: BlockRepositoryInterpreter => IO[A]): Future[A] = {

    def newMap: IO[swaydb.Map[Array[Byte], Array[Byte], IO]] = swaydb.memory.Map[Array[Byte], Array[Byte]]()

    for {
      db0 <- newMap
      db1 <- newMap
      db2 <- newMap
      db3 <- newMap
      newRepo = new BlockRepositoryInterpreter(db0, db1, db2, db3)
      result <- testBody(newRepo)
      _ <- db3.closeDatabase()
      _ <- db2.closeDatabase()
      _ <- db1.closeDatabase()
      _ <- db0.closeDatabase()
    } yield result
  }.toFuture

  val tests = Tests {
    test("getHeader from empty repository") - withNewRepo { repo =>
      for {
        headerEither <- repo.getHeader(blockHash).value
      } yield {
        assert(headerEither === Right(None))
      }
    }

    test("getBestHeader from empty repository") - withNewRepo { repo =>
      for {
        headerEither <- repo.bestHeader.value
      } yield {
        assert(headerEither === Left("Do not exist best block header"))
      }
    }

    test("put and getHeader") - withNewRepo { repo =>
      for {
        _ <- repo.put(block).value
        headerEither <- repo.getHeader(blockHash).value
      } yield {
        assert(headerEither === Right(Some(block.header)))
      }
    }

    test("put and bestHeader") - withNewRepo { repo =>
      for {
        _ <- repo.put(block).value
        headerEither <- repo.bestHeader.value
      } yield {
        assert(headerEither === Right(block.header))
      }
    }

    test("put and getTransactionHashes") - withNewRepo { repo =>
      for {
        _ <- repo.put(block).value
        transactionHashesEither <- repo.getTransactionHashes(blockHash).value
      } yield {
        assert(transactionHashesEither === Right(block.transactionHashes.toList))
      }
    }

    test("put and getSignatures") - withNewRepo { repo =>
      for {
        _ <- repo.put(block).value
        sigsEither <- repo.getSignatures(blockHash).value
      } yield {
        assert(sigsEither === Right(block.votes.toList))
      }
    }
  }
}
