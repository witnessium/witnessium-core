package org.witnessium.core
package model

import java.time.Instant
import eu.timepit.refined.refineV
import eu.timepit.refined.numeric.NonNegative
import org.scalacheck.{Arbitrary, Gen}
import scodec.bits.{BitVector, ByteVector}
import shapeless.nat._16
import shapeless.syntax.sized._

import datatype.{BigNat, MerkleTrieNode, UInt256BigInt, UInt256Bytes, UInt256Refine}
import util.refined.bitVector._

trait ModelArbitrary {

  implicit val arbitraryBigNat: Arbitrary[BigNat] = Arbitrary( for {
    bigint <- Arbitrary.arbitrary[BigInt]
  } yield refineV[NonNegative](bigint.abs).toOption.get)

  implicit val arbitraryUInt256BigInt: Arbitrary[UInt256BigInt] = Arbitrary(for{
    bytes <- Gen.containerOfN[Array, Byte](32, Arbitrary.arbitrary[Byte])
  } yield UInt256Refine.from(BigInt(1, bytes)).toOption.get)

  implicit val arbitraryUInt256Bytes: Arbitrary[UInt256Bytes] = Arbitrary(for {
    bytes <- Gen.containerOfN[Array, Byte](32, Arbitrary.arbitrary[Byte])
  } yield UInt256Refine.from(ByteVector.view(bytes)).toOption.get)

  implicit val arbitraryName: Arbitrary[Account.Name] = Arbitrary(for {
    size <- Gen.choose(6, 12)
    bytes <- Gen.containerOfN[Array, Byte](size, Arbitrary.arbitrary[Byte])
  } yield new Account.Name(ByteVector.view(bytes)))

  implicit val arbitraryAccount: Arbitrary[Account] = Arbitrary(Gen.frequency(
    (1, arbitraryAccountNamed.arbitrary),
    (9, arbitraryAccountUnnamed.arbitrary),
  ))

  implicit val arbitraryAccountNamed: Arbitrary[Account.Named] = Arbitrary(for {
    name <- arbitraryName.arbitrary
  } yield Account.Named(name))

  implicit val arbitraryAccountUnnamed: Arbitrary[Account.Unnamed] = Arbitrary(
    arbitraryAddress.arbitrary.map(Account.Unnamed(_))
  )

  implicit val arbitraryNameState: Arbitrary[NameState] = Arbitrary(for {
    size <- Gen.choose(1, 3)
    guardianList <- Gen.listOfN(size, arbitraryAccount.arbitrary)
    addresses <- arbitraryMultiSig.arbitrary
  } yield NameState(guardianList.headOption, addresses))

  implicit val arbitraryAddress: Arbitrary[Address] = Arbitrary(for {
    bytes <- Gen.containerOfN[Array, Byte](20, Arbitrary.arbitrary[Byte])
  } yield Address(ByteVector.view(bytes)).toOption.get)

  private def toBigNat(n: Int): BigNat = refineV[NonNegative](BigInt(n)).toOption.get

  implicit val arbitraryMultiSig: Arbitrary[MultiSig] = Arbitrary(for {
    size <- Gen.choose(1, 3)
    weights <- Gen.mapOfN[Address, BigNat](size, (for {
      address <- arbitraryAddress.arbitrary
      weight <- Gen.choose(1, 3)
    } yield (address, toBigNat(weight))))
    threshold <- Gen.choose(1, weights.toList.map(_._2.value.toInt).sum)
  } yield MultiSig(weights, toBigNat(threshold)))

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
    v <- Gen.choose(27, 34)
    r <- arbitraryUInt256BigInt.arbitrary
    s <- arbitraryUInt256BigInt.arbitrary
  } yield Signature(refineV[Signature.HeaderRange](v).toOption.get, r, s))

  implicit val arbitraryTransaction: Arbitrary[Transaction] = Arbitrary(for {
    networkId <- arbitraryBigNat.arbitrary
    inputSize <- Gen.choose(0, 10)
    outputSize <- Gen.choose(0, 10)
    inputs <- Gen.listOfN(inputSize, arbitraryUInt256Bytes.arbitrary)
    outputs <- Gen.listOfN(outputSize, arbitraryTuple2[Account, BigNat].arbitrary)
  } yield Transaction(networkId, inputs.toSet, outputs.toSet))

  implicit def arbitrarySigned[A](implicit aa: Arbitrary[A]): Arbitrary[Signed[A]] = Arbitrary(for {
    a <- aa.arbitrary
    sig <- arbitrarySignature.arbitrary
  } yield Signed(sig, a))

  implicit def arbitraryVerifiable[A: Arbitrary]: Arbitrary[Verifiable[A]] = Arbitrary(Gen.frequency(
    (1, Arbitrary.arbitrary[A].map(Genesis(_))),
    (9, arbitrarySigned[A].arbitrary),
  ))

  implicit val arbitraryBlockHeader: Arbitrary[BlockHeader] = Arbitrary(for {
    number <- arbitraryBigNat.arbitrary
    parentHash <- arbitraryUInt256Bytes.arbitrary
    stateRoot <- arbitraryUInt256Bytes.arbitrary
    transactionsRoot <- arbitraryUInt256Bytes.arbitrary
    long <- Gen.choose[Long](100000000000L, 10000000000000L)
  } yield BlockHeader(number, parentHash, stateRoot, transactionsRoot, Instant.ofEpochMilli(long.abs)))

  implicit val arbitraryBlock: Arbitrary[Block] = Arbitrary(for {
    heder <- arbitraryBlockHeader.arbitrary
    transactionHashes <- arbitrarySet[UInt256Bytes].arbitrary
    numberOfVotes <- Gen.choose(1, 10)
    votes <- Gen.listOfN(numberOfVotes, arbitrarySignature.arbitrary)
  } yield Block(heder, transactionHashes, votes.toSet))


  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  implicit val arbitraryMerkleTrieNode: Arbitrary[MerkleTrieNode] = Arbitrary(for {
    isLeaf <- Gen.oneOf(true, false)
    nibbleSize <- Gen.choose(0, 127)
    prefixBytes <- Gen.listOfN((nibbleSize + 1) / 2, Arbitrary.arbitrary[Byte])
    prefix = refineV[MerkleTrieNode.PrefixCondition]{
      BitVector.view(prefixBytes.toArray).take(nibbleSize * 4L)
    }.toOption.get
    node <- if (isLeaf) {
      arbitraryList[Byte].arbitrary.map{ byteList =>
        MerkleTrieNode.Leaf(prefix, ByteVector.view(byteList.toArray))
      }
    } else {
      Gen.containerOfN[Vector, Option[UInt256Bytes]](16, Gen.option(arbitraryUInt256Bytes.arbitrary)).map {
        unsizedChildren => MerkleTrieNode.Branch(prefix, unsizedChildren.sized(_16).get)
      }
    }
  } yield node)

  implicit val arbitraryGossipMessage: Arbitrary[GossipMessage] = {

    val blockSuggestionsArbitrary: Arbitrary[(BlockHeader, Set[UInt256Bytes])] = Arbitrary(for {
      header <- arbitraryBlockHeader.arbitrary
      transactionHashes <- arbitrarySet[UInt256Bytes].arbitrary
    } yield header -> transactionHashes)

    def blockVotesArbitrary(blockSuggestionsSize: Int): Arbitrary[Map[UInt256Bytes, Set[Signature]]] = {

      val blockVoteArbitrary: Arbitrary[(UInt256Bytes, Set[Signature])] = Arbitrary(for {
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
      blockSuggestionList <- Gen.listOfN(blockSuggestionsSize, blockSuggestionsArbitrary.arbitrary)
      blockVotes <- blockVotesArbitrary(blockSuggestionsSize).arbitrary
      newTransactions <- arbitrarySet[Transaction.Verifiable].arbitrary
    } yield GossipMessage(blockSuggestionList.toSet, blockVotes, newTransactions))
  }

  implicit val arbitraryNodeStatus: Arbitrary[NodeStatus] = Arbitrary(for{
    networkId <- arbitraryBigNat.arbitrary
    genesisHash <- arbitraryUInt256Bytes.arbitrary
    bestHash <- arbitraryUInt256Bytes.arbitrary
    number <- arbitraryBigNat.arbitrary
  } yield NodeStatus(networkId, genesisHash, bestHash, number))
}
