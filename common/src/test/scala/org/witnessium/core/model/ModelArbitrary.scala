package org.witnessium.core
package model

import java.time.Instant
import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.scalacheck.all._
import org.scalacheck.{Arbitrary, Gen}
import scodec.bits.{BitVector, ByteVector}
import shapeless.nat._16
import shapeless.syntax.sized._

import datatype.{MerkleTrieNode, UInt256Refine}
import util.refined.bitVector._

trait ModelArbitrary {

  implicit val arbitraryBigNat: Arbitrary[BigNat] = Arbitrary( for {
    bigint <- Arbitrary.arbitrary[BigInt]
  } yield refineV[NonNegative](bigint.abs).toOption.get)

  implicit val arbitraryUInt256BigInt: Arbitrary[UInt256Refine.UInt256BigInt] = Arbitrary(for{
    bytes <- Gen.containerOfN[Array, Byte](32, Arbitrary.arbitrary[Byte])
  } yield UInt256Refine.from(BigInt(1, bytes)).toOption.get)

  implicit val arbitraryUInt256Bytes: Arbitrary[UInt256Refine.UInt256Bytes] = Arbitrary(for {
    bytes <- Gen.containerOfN[Array, Byte](32, Arbitrary.arbitrary[Byte])
  } yield UInt256Refine.from(ByteVector.view(bytes)).toOption.get)

  implicit val arbitraryAddress: Arbitrary[Address] = Arbitrary(for {
    bytes <- Gen.containerOfN[Array, Byte](20, Arbitrary.arbitrary[Byte])
  } yield Address(ByteVector.view(bytes)).toOption.get)

  implicit def arbitraryTuple2[A:Arbitrary, B:Arbitrary]: Arbitrary[(A, B)] = Arbitrary(for{
    a <- Arbitrary.arbitrary[A]
    b <- Arbitrary.arbitrary[B]
  } yield (a, b))

  implicit def arbitraryList[A](implicit aa: Arbitrary[A]): Arbitrary[List[A]] = Arbitrary(Gen.sized { size =>
    Gen.containerOfN[List, A](size, aa.arbitrary)
  })

  implicit def arbitrarySet[A:Arbitrary]: Arbitrary[Set[A]] = Arbitrary(arbitraryList[A].arbitrary.map(_.toSet))

  implicit def arbitraryMap[A:Arbitrary, B:Arbitrary]: Arbitrary[Map[A, B]] =
    Arbitrary(arbitraryList[(A, B)].arbitrary.map(_.toMap))

  implicit val arbitrarySignature: Arbitrary[Signature] = Arbitrary(for {
    v <- Arbitrary.arbitrary[Int Refined Signature.HeaderRange]
    r <- arbitraryUInt256BigInt.arbitrary
    s <- arbitraryUInt256BigInt.arbitrary
  } yield Signature(v, r, s))

  implicit val arbitraryTransaction: Arbitrary[Transaction] = Arbitrary(for {
    networkId <- arbitraryBigNat.arbitrary
    inputSize <- Gen.choose(0, 10)
    outputSize <- Gen.choose(0, 10)
    inputs <- Gen.listOfN(inputSize, arbitraryAddress.arbitrary)
    outputs <- Gen.listOfN(outputSize, arbitraryTuple2[Address, BigNat].arbitrary)
  } yield Transaction(networkId, inputs.toSet, outputs.toSet))

  implicit def arbitrarySigned[A](implicit aa: Arbitrary[A]): Arbitrary[Signed[A]] = Arbitrary(for {
    a <- aa.arbitrary
    sig <- arbitrarySignature.arbitrary
  } yield Signed(a, sig))

  implicit val arbitraryState: Arbitrary[State] = Arbitrary(for {
    unused <- arbitrarySet[(Address, UInt256Refine.UInt256Bytes)].arbitrary
    transactions <- arbitrarySet[Transaction].arbitrary
  } yield State(unused, transactions))

  implicit val arbitraryBlockHeader: Arbitrary[BlockHeader] = Arbitrary(for {
    number <- arbitraryBigNat.arbitrary
    parentHash <- arbitraryUInt256Bytes.arbitrary
    stateRoot <- arbitraryUInt256Bytes.arbitrary
    transactionsRoot <- arbitraryUInt256Bytes.arbitrary
    long <- Gen.choose[Long](100000000000L, 10000000000000L)
  } yield BlockHeader(number, parentHash, stateRoot, transactionsRoot, Instant.ofEpochMilli(long.abs)))

  implicit val arbitraryBlock: Arbitrary[Block] = Arbitrary(for {
    heder <- arbitraryBlockHeader.arbitrary
    transactionHashes <- arbitrarySet[UInt256Refine.UInt256Bytes].arbitrary
  } yield Block(heder, transactionHashes))

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  implicit val arbitraryMerkleTrieNode: Arbitrary[MerkleTrieNode] = Arbitrary(for {
    isLeaf <- Gen.oneOf(true, false)
    nibbleSize <- Gen.choose(0, 127)
    prefixBytes <- Gen.listOfN((nibbleSize + 1) / 4, Arbitrary.arbitrary[Byte])
    prefix = refineV[MerkleTrieNode.PrefixCondition]{
      BitVector.view(prefixBytes.toArray).take(nibbleSize * 4L)
    }.toOption.get
    node <- if (isLeaf) {
      arbitraryList[Byte].arbitrary.map{ byteList =>
        MerkleTrieNode.Leaf(prefix, ByteVector.view(byteList.toArray))
      }
    } else {
      Gen.containerOfN[Vector, UInt256Refine.UInt256Bytes](16, arbitraryUInt256Bytes.arbitrary).map {
        unsizedChildren => MerkleTrieNode.Branch(prefix, unsizedChildren.sized(_16).get)
      }
    }
  } yield node)

  implicit val arbitraryGossipMessage: Arbitrary[GossipMessage] = {
    def blockVotesArbitrary(blockSuggestionsSize: Int): Arbitrary[Map[UInt256Refine.UInt256Bytes, Set[Signature]]] = {

      val blockVoteArbitrary: Arbitrary[(UInt256Refine.UInt256Bytes, Set[Signature])] = Arbitrary(for {
        hash <- arbitraryUInt256Bytes.arbitrary
        numberOfSig <- Gen.choose(0, 4)
        sigs <- Gen.listOfN(numberOfSig, arbitrarySignature.arbitrary)
      } yield (hash, sigs.toSet))

      Arbitrary(for {
        list <- Gen.listOfN(blockSuggestionsSize, blockVoteArbitrary.arbitrary)
      } yield list.toMap)
    }

    Arbitrary(for {
      blockSuggestionsSize <- Gen.choose(0, 4)
      blockSuggestionList <- Gen.listOfN(blockSuggestionsSize, arbitraryBlock.arbitrary)
      blockVotes <- blockVotesArbitrary(blockSuggestionsSize).arbitrary
      newTransactions <- arbitrarySet[Transaction].arbitrary
    } yield GossipMessage(blockSuggestionList.toSet, blockVotes, newTransactions))
  }

  implicit val arbitraryNodeStatus: Arbitrary[NodeStatus] = Arbitrary(for{
    networkId <- arbitraryBigNat.arbitrary
    genesisHash <- arbitraryUInt256Bytes.arbitrary
    bestHash <- arbitraryUInt256Bytes.arbitrary
    number <- arbitraryBigNat.arbitrary
  } yield NodeStatus(networkId, genesisHash, bestHash, number))
}
