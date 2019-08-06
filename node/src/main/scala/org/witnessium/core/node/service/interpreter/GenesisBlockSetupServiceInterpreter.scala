package org.witnessium.core
package node
package service
package interpreter

import java.time.Instant
import cats.data.NonEmptyList
import cats.implicits._
import eu.timepit.refined.refineMV
import eu.timepit.refined.numeric.NonNegative
import swaydb.data.IO
import datatype.{BigNat, UInt256Bytes, UInt256Refine}
import model.{Address, Block, BlockHeader, Genesis, NetworkId, State, Transaction}
import repository.{BlockRepository, GossipRepository, StateRepository, TransactionRepository}
import util.SwayIOCats._

class GenesisBlockSetupServiceInterpreter(
  networkId: NetworkId,
  genesisInstant: Instant,
  initialDistribution: Map[Address, BigNat],
  blockRepository: BlockRepository[IO],
  stateRepository: StateRepository[IO],
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

    val genesisBlockHeader: BlockHeader = BlockHeader(
      number = refineMV[NonNegative](BigInt(1)),
      parentHash = crypto.hash[UInt256Bytes](UInt256Refine.EmptyBytes),
      stateRoot = crypto.hash[State](state),
      transactionsRoot = crypto.hash[List[Transaction]](List(transaction)),
      timestamp = genesisInstant,
    )

    val genesisBlock: Block = Block(
      header = genesisBlockHeader,
      transactionHashes = Set(transactionHash),
      votes = Set.empty,
    )

    gossipRepository.genesisHash = crypto.hash(genesisBlockHeader)

    NonEmptyList.of(
      state.unused.toList.traverse{ case (address, transactionHash) =>
        stateRepository.put(address, transactionHash)
      },
      transactionRepository.put(Genesis(transaction)),
      blockRepository.put(genesisBlock),
    ).sequence.map(_ => ())
  }
}
