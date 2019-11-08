package org.witnessium.core
package node.crypto

import scala.util.Random

import cats.{Id, Monad}
import cats.data.{EitherT, StateT}
import cats.implicits._
import io.iteratee.Enumerator
import scodec.bits.BitVector

import datatype.{MerkleTrieNode, UInt256Bytes}
import model.Address
import MerkleTrie._
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.rng.Seed
import model.ModelArbitrary
import utest._

object MerkleTrieTest extends TestSuite with ModelArbitrary {

  implicit val emptyNodeStore: NodeStore[Id] = new NodeStore[Id] {
    def get(hash: UInt256Bytes): Either[String, Option[MerkleTrieNode]] = Right(None)
  }

  val emptyState = MerkleTrieState.empty

  def sample: (BitVector, Address) = {
    val address = Arbitrary.arbitrary[Address].pureApply(Gen.Parameters.default, Seed.random())
    val key = hash[Address](address)
    (key.toBytes.bits, address)
  }
  val (sampleKey, sampleValue) = sample

  def enumeratorToList[F[_]: Monad, A](
    enumerator: Enumerator[EitherT[F, String, *], A]
  ): StateT[EitherT[F, String, *], MerkleTrieState, List[A]] = StateT.liftF(
    enumerator.toVector.map(_.toList)
  )

  val tests = Tests {
    test("get from empty"){
      val program = get[Id, Address](sampleKey)
      val result = program.run(emptyState)

      assert(result.value == Right((emptyState, None)))
    }

    test("put and get"){
      val program = for {
        _ <- put[Id, Address](sampleKey, sampleValue)
        resultValue <- get[Id, Address](sampleKey)
      } yield resultValue
      val result = (program run emptyState).map(_._2)

      assert(result.value == Right(Some(sampleValue)))
    }

    test("put several samples and from"){

      val numberOfSample = 10
      val samples = List.fill(numberOfSample)(sample)
      val expected = samples.sortBy(_._1.toHex)

      val program = for {
        _ <- samples.traverse{ case (k, v) => put[Id, Address](k, v)}
        resultEnumerator <- from[Id, Address](BitVector.empty)
        resultValue <- enumeratorToList(resultEnumerator)
      } yield resultValue
      val result = (program run emptyState).map(_._2)


      assert(result.value == Right(expected))
    }

    test("put 2 samples in the same branch and remove one and from") {

      val sample1 = Address.fromHex("5d80ffff5c8001011833515d00017246d8ff1de1").toOption.get
      val sample1Hash = hash[Address](sample1).bits
      val sample2 = Address.fromHex("805f7f010137695500800106001b7f0000ff0100").toOption.get
      val sample2Hash = hash[Address](sample2).bits

      val program = for {
        _ <- put[Id, Address](sample1Hash, sample1)
        _ <- put[Id, Address](sample2Hash, sample2)
        _ <- removeByKey[Id, Address](hash[Address](sample1).bits)
        resultEnumerator <- from[Id, Address](BitVector.empty)
        resultValue <- enumeratorToList(resultEnumerator)
      } yield resultValue
      val result = (program run emptyState).map(_._2)

      assert(result.value == Right(List((sample2Hash, sample2))))

    }

    test("put some fixed samples and remove one and from"){

      val samples = List(
        "7ba17f7f018ffefa01007fa7ffde7affeaff2de8",
        "55ff7f80015e009bff7b533b80ffffd5ffee2030",
        "1080ff64648801831d7f00d180f9907f00ff6be5",
      ).map(Address.fromHex).map(_.toOption.get).map{ address => (hash[Address](address).bits, address) }

      val expected = samples.tail.sortBy(_._1.toHex)

      val program = for {
        _ <- samples.traverse{ case (k, v) => put[Id, Address](k, v)}
        _ <- removeByKey[Id, Address](samples.head._1)
        resultEnumerator <- from[Id, Address](BitVector.empty)
        resultValue <- enumeratorToList(resultEnumerator)
      } yield resultValue
      val result = (program run emptyState)

      if (result.map(_._2).value != Right(expected)) {

        println(s"==========================================")
        println(s"===> samples")
        samples foreach println
        println(s"===> expected")
        expected foreach println
        println(s"===> state: ${result.value}")
        println(s"==========================================")
      }

      assert(result.map(_._2).value == Right(expected))
    }

    test("put several samples and remove one and from"){

      val numberOfSample = 100
      val samples = List.fill(numberOfSample)(sample)
      val expected = samples.tail.sortBy(_._1.toHex)

      val program = for {
        _ <- samples.traverse{ case (k, v) => put[Id, Address](k, v)}
        _ <- removeByKey[Id, Address](samples.head._1)
        resultEnumerator <- from[Id, Address](BitVector.empty)
        resultValue <- enumeratorToList(resultEnumerator)
      } yield resultValue
      val result = (program run emptyState).map(_._2)

      if (result.value != Right(expected)) {

        println(s"==========================================")
        println(s"===> samples")
        samples foreach println
        println(s"===> expected")
        expected foreach println
        println(s"===> state: ${result.value}")
        println(s"==========================================")
      }

      assert(result.value == Right(expected))
    }

    test("put several samples with different order and still have the same state"){

      val numberOfSample = 100
      val numberOfRemoved = 10
      val samples = List.fill(numberOfSample)(sample)
      val toBeRemoved = samples.take(numberOfRemoved)
      val shuffled = Random.shuffle(samples)

      val program = for {
        _ <- samples.traverse{ case (k, v) => put[Id, Address](k, v) }
        _ <- toBeRemoved.traverse{ case (k, _) => removeByKey[Id, Address](k) }
        resultEnumerator <- from[Id, Address](BitVector.empty)
        resultValue <- enumeratorToList(resultEnumerator)
      } yield resultValue
      val result = (program run emptyState).map(_._1)

      val program2 = for {
        _ <- shuffled.traverse{ case (k, v) => put[Id, Address](k, v) }
        _ <- toBeRemoved.traverse{ case (k, _) => removeByKey[Id, Address](k) }
        resultEnumerator <- from[Id, Address](BitVector.empty)
        resultValue <- enumeratorToList(resultEnumerator)
      } yield resultValue
      val result2 = (program2 run emptyState).map(_._1)

      if (result.value != result2.value) {

        println(s"==========================================")
        println(s"===> samples")
        samples foreach println
        println(s"===> shuffled")
        shuffled foreach println
        println(s"===> removed: $toBeRemoved")
        println(s"===> state1: ${result.value}")
        println(s"===> state2: ${result2.value}")
        println(s"==========================================")
      }
      assert(result.value == result2.value)
    }
  }
}
