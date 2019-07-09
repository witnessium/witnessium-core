package org.witnessium.core
package node
package repository
package interpreter

import scodec.bits.ByteVector
import swaydb.serializers.Default._

import org.witnessium.core.codec.byte.ByteEncoder
import datatype.UInt256Refine
import model.{Block, BlockHeader}

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.rng.Seed
import org.witnessium.core.model.ModelArbitrary
import utest._

object BlockRepositoryInterpreterTest extends TestSuite with ModelArbitrary {

  def newSwayDb = swaydb.memory.zero.Map[Array[Byte], Array[Byte]]().get

  def newRepo: BlockRepositoryInterpreter = new BlockRepositoryInterpreter(newSwayDb, newSwayDb, newSwayDb)

  val block = Arbitrary.arbitrary[Block].pureApply(Gen.Parameters.default, Seed.random())
  val blockHash = UInt256Refine.from{
    ByteVector.view(crypto.keccak256(ByteEncoder[BlockHeader].encode(block.header).toArray))
  }.toOption.get

  val tests = Tests {
    test("getHeader from empty repository") {
      val repo = newRepo
      repo.getHeader(blockHash).map {
        assertMatch(_){ case Left(_) => }
      }
    }

    test("put and getHeader") {
      val repo = newRepo
      for {
        _ <- repo.put(block)
        headerEither <- repo.getHeader(blockHash)
      } yield {
        assertMatch(headerEither){ case Right(header) if header === block.header => }
      }
    }

    test("put and getTransactionHashes") {
      val repo = newRepo
      for {
        _ <- repo.put(block)
        transactionHashesEither <- repo.getTransactionHashes(blockHash)
      } yield {
        assertMatch(transactionHashesEither){
          case Right(transactionHashes) if transactionHashes === block.transactionHashes.toList =>
        }
      }
    }

    test("put and getSignatures") {
      val repo = newRepo
      for {
        _ <- repo.put(block)
        sigsEither <- repo.getSignatures(blockHash)
      } yield {
        assertMatch(sigsEither){ case Right(sigs) if sigs === block.signatures.toList => }
      }
    }
  }
}
