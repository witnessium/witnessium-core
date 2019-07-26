package org.witnessium.core
package node
package repository
package interpreter

import scala.concurrent.Future
import scodec.bits.ByteVector
import swaydb.data.IO
import swaydb.serializers.Default._

import org.witnessium.core.codec.byte.ByteEncoder
import datatype.UInt256Refine
import model.{Block, BlockHeader}

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.rng.Seed
import org.witnessium.core.model.ModelArbitrary
import utest._

object BlockRepositoryInterpreterTest extends TestSuite with ModelArbitrary {

  val block = Arbitrary.arbitrary[Block].pureApply(Gen.Parameters.default, Seed.random())

  val sigSet = (for {
    numberOfSig <- Gen.choose(0, 4)
    sigList <- Gen.listOfN(numberOfSig, arbitrarySignature.arbitrary)
  } yield sigList.toSet).pureApply(Gen.Parameters.default, Seed.random())

  val blockHash = UInt256Refine.from{
    ByteVector.view(crypto.keccak256(ByteEncoder[BlockHeader].encode(block.header).toArray))
  }.toOption.get

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
        _ <- repo.put(block, sigSet)
        headerEither <- repo.getHeader(blockHash)
      } yield {
        assert(headerEither === Right(block.header))
      }
    }

    test("put and getTransactionHashes") - withNewRepo { repo =>
      for {
        _ <- repo.put(block, sigSet)
        transactionHashesEither <- repo.getTransactionHashes(blockHash)
      } yield {
        assert(transactionHashesEither === Right(block.transactionHashes.toList))
      }
    }

    test("put and getSignatures") - withNewRepo { repo =>
      for {
        _ <- repo.put(block, sigSet)
        sigsEither <- repo.getSignatures(blockHash)
      } yield {
        assert(sigsEither === Right(sigSet.toList))
      }
    }
  }
}
