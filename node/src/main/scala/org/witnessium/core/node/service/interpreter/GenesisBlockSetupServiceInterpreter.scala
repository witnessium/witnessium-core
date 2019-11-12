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
import datatype.{BigNat, UInt256Bytes, UInt256Refine}
import model.{Address, Block, BlockHeader, Genesis, NetworkId, State, Transaction}
import repository.{BlockRepository, GossipRepository, TransactionRepository}
import service.StateService
import util.SwayIOCats._

class GenesisBlockSetupServiceInterpreter(
  networkId: NetworkId,
  genesisInstant: Instant,
  initialDistribution: Map[Address, BigNat],
  blockRepository: BlockRepository[IO],
  stateService: StateService[IO],
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
      number = refineMV[NonNegative](BigInt(0)),
      parentHash = crypto.hash[UInt256Bytes](UInt256Refine.EmptyBytes),
      stateRoot = stateService.hash(state),
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

    NonEmptyList.of(
      EitherT.right[String](stateService.put(state)),
      transactionRepository.put(Genesis(transaction)),
      blockRepository.put(genesisBlock),
    ).map(_.value).sequence.map(_ => ())
  }
}
