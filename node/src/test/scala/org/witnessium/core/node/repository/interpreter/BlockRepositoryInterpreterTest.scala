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
      newRepo = new BlockRepositoryInterpreter(db0, db1, db2)
      result <- testBody(newRepo)
      _ <- db2.closeDatabase()
      _ <- db1.closeDatabase()
      _ <- db0.closeDatabase()
    } yield result
  }.toFuture

  val tests = Tests {
    test("getHeader from empty repository") - withNewRepo { repo =>
      for {
        headerEither <- repo.getHeader(blockHash)
      } yield {
        assertMatch(headerEither){ case Left(_) => }
      }
    }

    test("put and getHeader") - withNewRepo { repo =>
      for {
        _ <- repo.put(block)
        headerEither <- repo.getHeader(blockHash)
      } yield {
        assert(headerEither === Right(block.header))
      }
    }

    test("put and getTransactionHashes") - withNewRepo { repo =>
      for {
        _ <- repo.put(block)
        transactionHashesEither <- repo.getTransactionHashes(blockHash)
      } yield {
        assert(transactionHashesEither === Right(block.transactionHashes.toList))
      }
    }

    test("put and getSignatures") - withNewRepo { repo =>
      for {
        _ <- repo.put(block)
        sigsEither <- repo.getSignatures(blockHash)
      } yield {
        assert(sigsEither === Right(block.votes.toList))
      }
    }
  }
}
