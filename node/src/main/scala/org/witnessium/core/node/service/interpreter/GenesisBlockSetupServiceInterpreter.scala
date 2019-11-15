package org.witnessium.core
package node
package service
package interpreter

import java.time.Instant
import cats.data.{EitherT, NonEmptyList}
import cats.implicits._
import eu.timepit.refined.refineMV
import eu.timepit.refined.numeric.NonNegative
import swaydb.data.IO
import crypto.MerkleTrie
import crypto.MerkleTrie.MerkleTrieState
import datatype.{BigNat, MerkleTrieNode, UInt256Bytes, UInt256Refine}
import model.{Address, Block, BlockHeader, Genesis, NetworkId, State, Transaction}
import repository.{BlockRepository, GossipRepository, TransactionRepository}
import store.HashStore
import util.SwayIOCats._

class GenesisBlockSetupServiceInterpreter(
  networkId: NetworkId,
  genesisInstant: Instant,
  initialDistribution: Map[Address, BigNat],
  blockRepository: BlockRepository[IO],
  transactionRepository: TransactionRepository[IO],
  gossipRepository: GossipRepository[IO]
) extends GenesisBlockSetupService[IO] {

  def apply(): IO[Unit] = {

    val transaction = Transaction(
      networkId = networkId,
      inputs = Set.empty,
      outputs = initialDistribution.toSet,
    )

    val transactionHash = crypto.hash[Transaction](transaction)

    val state = State(
      unused = initialDistribution.mapValues(_ => transactionHash).toSet,
      transactions = Set(Genesis(transaction)),
    )

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    val stateRoot = {
      implicit val dummyNodeStore: HashStore[IO, MerkleTrieNode] =  new HashStore[IO, MerkleTrieNode] {
        def get(hash: UInt256Bytes): EitherT[IO, String, Option[MerkleTrieNode]] = EitherT.pure(None)
        def put(node: MerkleTrieNode): EitherT[IO, String, Unit] = EitherT.pure(())
      }

      (for {
        mtState <- state.unused.toList.traverse { case (address, txHash) =>
          MerkleTrie.put((address.bytes ++ txHash).bits, ())
        }.map(_ => ()) runS MerkleTrieState.empty
        rootHash <- EitherT.fromOption[IO](mtState.root, s"Empty root: $mtState")
      } yield rootHash).value.map {
        case Right(hash) => hash
        case Left(msg) => throw new Exception(msg)
      }.get
    }

    val genesisBlockHeader: BlockHeader = BlockHeader(
      number = refineMV[NonNegative](BigInt(0)),
      parentHash = crypto.hash[UInt256Bytes](UInt256Refine.EmptyBytes),
      stateRoot = stateRoot,
      transactionsRoot = crypto.hash[List[Transaction]](List(transaction)),
      timestamp = genesisInstant,
    )

    val genesisBlock: Block = Block(
      header = genesisBlockHeader,
      transactionHashes = Set(transactionHash),
      votes = Set.empty,
    )

    gossipRepository.genesisHash = crypto.hash(genesisBlockHeader)

    scribe.info(s"Genesis Block Hash: ${gossipRepository.genesisHash}")
    scribe.info(s"Genesis Block: $genesisBlock")

    // TODO update state
    NonEmptyList.of(
      transactionRepository.put(Genesis(transaction)),
      blockRepository.put(genesisBlock),
    ).map(_.value).sequence.map(_ => ())
  }
}
