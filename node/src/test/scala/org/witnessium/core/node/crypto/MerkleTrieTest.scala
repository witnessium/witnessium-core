package org.witnessium.core
package node
package crypto

import scala.util.Random

import cats.Id
import cats.data.{EitherT, Kleisli, StateT}
import cats.effect.Sync
import cats.implicits._
import monix.tail.Iterant
import scodec.bits.{BitVector, ByteVector}

import datatype.{UInt256Bytes, UInt256Refine}
import model.Address
import MerkleTrie._
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.rng.Seed
import model.ModelArbitrary
import utest._

object MerkleTrieTest extends TestSuite with ModelArbitrary {

  implicit val emptyNodeStore: NodeStore[Id] = Kleisli{ (_: UInt256Bytes) => EitherT.pure(None) }

  implicit val idSync: Sync[Id] = new Sync[Id] {

    // Members declared in cats.Applicative
    def pure[A](x: A): Id[A] = x

    // Members declared in cats.ApplicativeError
    def handleErrorWith[A](fa: Id[A])(f: Throwable => Id[A]): Id[A] = fa
    def raiseError[A](e: Throwable): cats.Id[A] = throw new Exception(e)

    // Members declared in cats.effect.Bracket
    def bracketCase[A, B](acquire: Id[A])(use: A => Id[B])
      (release: (A, cats.effect.ExitCase[Throwable]) => Id[Unit]): Id[B] = use(acquire)

    // Members declared in cats.FlatMap
    def flatMap[A, B](fa: Id[A])(f: A => Id[B]): Id[B] = f(fa)
    def tailRecM[A, B](a: A)(f: A => Id[Either[A,B]]): Id[B] = f(a) match {
      case Left(a1) => tailRecM(a1)(f)
      case Right(b) => b
    }

    // Members declared in cats.effect.Sync
    def suspend[A](thunk: => Id[A]): Id[A] = thunk
  }

  val emptyState = MerkleTrieState.empty

  def sample: (BitVector, Address) = {
    val address = Arbitrary.arbitrary[Address].pureApply(Gen.Parameters.default, Seed.random())
    val key = hash[Address](address)
    (key.toBytes.bits, address)
  }
  val (sampleKey, sampleValue) = sample

  def iterantToList[F[_]: Sync, A](
    iterant: Iterant[EitherT[F, String, *], A]
  ): StateT[EitherT[F, String, *], MerkleTrieState, List[A]] = StateT.liftF(iterant.toListL)

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

    test("put, remove and from"){
      val program = for {
        _ <- put[Id, Address](sampleKey, sampleValue)
        _ <- removeByKey[Id](sampleKey)
        resultIterant <- from[Id, Address](BitVector.empty)
        resultValue <- iterantToList(resultIterant)
      } yield resultValue
      val result = program run emptyState

      assert(
        (result.value.map(_._1.root) == Right(None)) &&
          (result.value.map(_._2) == Right(Nil))
      )
    }

    test("put several samples and from"){

      val numberOfSample = 10
      val samples = List.fill(numberOfSample)(sample)
      val expected = samples.sortBy(_._1.toHex)

      val program = for {
        _ <- samples.traverse{ case (k, v) => put[Id, Address](k, v)}
        resultIterant <- from[Id, Address](BitVector.empty)
        resultValue <- iterantToList(resultIterant)
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
        _ <- removeByKey[Id](hash[Address](sample1).bits)
        resultIterant <- from[Id, Address](BitVector.empty)
        resultValue <- iterantToList(resultIterant)
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
        _ <- removeByKey[Id](samples.head._1)
        resultIterant <- from[Id, Address](BitVector.empty)
        resultValue <- iterantToList(resultIterant)
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
        _ <- removeByKey[Id](samples.head._1)
        resultIterant <- from[Id, Address](BitVector.empty)
        resultValue <- iterantToList(resultIterant)
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
        _ <- toBeRemoved.traverse{ case (k, _) => removeByKey[Id](k) }
        resultIterant <- from[Id, Address](BitVector.empty)
        resultValue <- iterantToList(resultIterant)
      } yield resultValue
      val result = (program run emptyState).map(_._1)

      val program2 = for {
        _ <- shuffled.traverse{ case (k, v) => put[Id, Address](k, v) }
        _ <- toBeRemoved.traverse{ case (k, _) => removeByKey[Id](k) }
        resultIterant <- from[Id, Address](BitVector.empty)
        resultValue <- iterantToList(resultIterant)
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

    test("put some fixed samples and from"){

      val Right(v) = for {
        bytes <- ByteVector.fromHexDescriptive("46c36f995d3385542388294949f827be3d7dfbaa2ef61357451b1337f2328cf0")
        refined <- UInt256Refine.from(bytes)
      } yield refined

      val samples = List(
        "2651c2bb06b558ffe480fad693d02e976befbb12",
        "26e5256124649dc404e2cdc71034c97cca48fdac",
        "40ba8d2382874b93158a2ab5f0a0c8ecdb062abc",
      ).map(Address.fromHex).map(_.toOption.get).map{ _.bytes ++ v }

      val prefix = ByteVector.fromHex("2651c2bb06b558ffe480fad693d02e976befbb12").get

      val expected = List((prefix ++ v).bits)

      val program = for {
        _ <- samples.traverse{ case bytes => put[Id, ByteVector](bytes.bits, ByteVector.empty)}
        resultIterant <- from[Id, ByteVector](prefix.bits)
        resultValue <- iterantToList(resultIterant.takeWhile{ case (k, v) =>
          scribe.debug(s"==> $k -> $v")
          scribe.debug(s"===> take next?: ${k startsWith prefix.bits}")

          k startsWith prefix.bits
        })
      } yield resultValue
      val result = (program runA emptyState).value.map{
        (list: List[(BitVector, ByteVector)]) => list.map(_._1)
      }

      if (result != Right(expected)) {

        println(s"==========================================")
        println(s"===> samples")
        samples foreach println
        println(s"===> expected")
        expected foreach println
        println(s"===> result: ${result}")
        println(s"==========================================")
      }

      assert(result == Right(expected))
    }
  }
}
