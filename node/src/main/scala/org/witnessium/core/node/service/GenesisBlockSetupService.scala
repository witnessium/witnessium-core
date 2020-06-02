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
import codec.byte.ByteEncoder
import datatype.{BigNat, MerkleTrieNode, UInt256Bytes, UInt256Refine}
import model.{Account, Block, BlockHeader, Genesis, NetworkId, Transaction}
import repository.StateRepository._
import store.HashStore

trait GenesisBlockSetupService[F[_]] {
  def apply(): F[Unit]
}

object GenesisBlockSetupService {

  def getGenesisBlock(
    networkId: NetworkId,
    genesisInstant: Instant,
    initialDistribution: Map[Account, BigNat],
  ): (Block, MerkleTrieState, Transaction.Verifiable) = {

    val transaction = Transaction(
      networkId = networkId,
      inputs = Set.empty,
      outputs = initialDistribution.toSet,
    )

    val transactionHash = crypto.hash[Transaction](transaction)

    val Right(state) = {
      implicit val dummyNodeStore: HashStore[Id, MerkleTrieNode] =  new HashStore[Id, MerkleTrieNode] {
        def get(hash: UInt256Bytes): EitherT[Id, String, Option[MerkleTrieNode]] = EitherT.pure(None)
        def put(node: MerkleTrieNode): EitherT[Id, String, Unit] = EitherT.pure(())
      }

      initialDistribution.keys.toList.traverse{ account =>
          MerkleTrie.put((ByteEncoder[Account].encode(account) ++ transactionHash).bits, ())
      }.runS(MerkleTrieState.empty).value
    }

    val Some(stateRoot) = state.root

    val genesisBlockHeader: BlockHeader = BlockHeader(
      number = refineMV[NonNegative](BigInt(0)),
      parentHash = crypto.hash[UInt256Bytes](UInt256Refine.EmptyBytes),
      namesRoot = crypto.hash[UInt256Bytes](UInt256Refine.EmptyBytes),
      stateRoot = stateRoot,
      transactionsRoot = crypto.hash[List[Transaction]](List(transaction)),
      timestamp = genesisInstant,
    )

    val genesisBlock: Block = Block(
      header = genesisBlockHeader,
      transactionHashes = Set(transactionHash),
      votes = Set.empty,
    )

    (genesisBlock, state, Genesis(transaction))
  }

}
