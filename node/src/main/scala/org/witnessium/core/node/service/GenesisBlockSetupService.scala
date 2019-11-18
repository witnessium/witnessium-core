package org.witnessium.core
package node

import java.time.Instant
import cats.Id
import cats.data.EitherT
import cats.implicits._
import eu.timepit.refined.refineMV
import eu.timepit.refined.numeric.NonNegative
import crypto.MerkleTrie
import crypto.MerkleTrie.MerkleTrieState
import datatype.{BigNat, MerkleTrieNode, UInt256Bytes, UInt256Refine}
import model.{Address, Block, BlockHeader, Genesis, NetworkId, State, Transaction}
import store.HashStore

trait GenesisBlockSetupService[F[_]] {
  def apply(): F[Unit]
}

object GenesisBlockSetupService {

  def getGenesisBlock(
    networkId: NetworkId,
    genesisInstant: Instant,
    initialDistribution: Map[Address, BigNat],
  ): (Block, Transaction.Verifiable) = {

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
      implicit val dummyNodeStore: HashStore[Id, MerkleTrieNode] =  new HashStore[Id, MerkleTrieNode] {
        def get(hash: UInt256Bytes): EitherT[Id, String, Option[MerkleTrieNode]] = EitherT.pure(None)
        def put(node: MerkleTrieNode): EitherT[Id, String, Unit] = EitherT.pure(())
      }

      (for {
        mtState <- state.unused.toList.traverse { case (address, txHash) =>
          MerkleTrie.put((address.bytes ++ txHash).bits, ())
        }.map(_ => ()) runS MerkleTrieState.empty
        rootHash <- EitherT.fromOption[Id](mtState.root, s"Empty root: $mtState")
      } yield rootHash).value match {
        case Right(hash) => hash
        case Left(msg) => throw new Exception(msg)
      }
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

    (genesisBlock, Genesis(transaction))
  }

}
