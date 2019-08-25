package org.witnessium.core
package node
package repository
package interpreter

import java.time.Instant
import scala.concurrent.Future
import cats.Id
import cats.data.EitherT
import cats.effect.concurrent.Ref
import eu.timepit.refined.refineV
import eu.timepit.refined.numeric.NonNegative
import swaydb.data.IO
import swaydb.serializers.Default._

import datatype.{BigNat, MerkleTrieNode, UInt256Bytes, UInt256Refine}
import crypto.{KeyPair, MerkleTrie}
import model.{Address, Block, BlockHeader, Genesis, Signed, State, Transaction}
import service.interpreter.StateServiceInterpreter
import util.SwayIOCats._

import org.witnessium.core.model.ModelArbitrary
import utest._

object GossipRepositoryInterpreterTest extends TestSuite with ModelArbitrary {

  def bignat(n: Int): BigNat = refineV[NonNegative](BigInt(n)).toOption.get

  val initialOwner: KeyPair = KeyPair.generate()
  val initialAddress: Address = Address.fromPublicKey(crypto.keccak256)(initialOwner.privateKey)
  val initialAmount: BigNat = bignat(10000)

  val initialTx = Transaction(networkId = bignat(1), inputs = Set.empty, outputs = Set((initialAddress, initialAmount)))
  val initialTxHash = crypto.hash[Transaction](initialTx)
  val state = State(unused = Set((initialAddress, initialTxHash)), transactions = Set(Genesis(initialTx)))

  implicit val emptyNodeStore: MerkleTrie.NodeStore[Id] = new MerkleTrie.NodeStore[Id] {
    def get(hash: UInt256Bytes): Either[String, Option[MerkleTrieNode]] = Right(None)
  }

  val stateRoot: UInt256Bytes = (for {
    mtState <- StateServiceInterpreter.stateToMerkleTrieState[Id](state) runS MerkleTrie.MerkleTrieState.empty
    rootHash <- EitherT.fromOption[Id](mtState.root, s"Empty root: $mtState")
  } yield rootHash).value.toOption.get

  val genesisBlockHeader: BlockHeader = BlockHeader(
    number = bignat(0),
    parentHash = crypto.hash[UInt256Bytes](UInt256Refine.EmptyBytes),
    stateRoot = stateRoot,
    transactionsRoot = crypto.hash[List[Transaction]](List(initialTx)),
    timestamp = Instant.parse("2019-08-01T09:00:00Z"),
  )

  val genesisHash: UInt256Bytes = crypto.hash[BlockHeader](genesisBlockHeader)

  val genesisBlock: Block = Block(
    header = genesisBlockHeader,
    transactionHashes = Set(initialTxHash),
    votes = Set.empty,
  )

  val keypair1: KeyPair = KeyPair.generate()
  val address1: Address = Address.fromPublicKey(crypto.keccak256)(keypair1.privateKey)

  val tx = Transaction(networkId = bignat(1), inputs = Set(initialTxHash), outputs = Set((address1, bignat(100))))
  val sig = initialOwner.sign(crypto.hash(tx).toArray).toOption.get
  val signedTx = Signed(sig, tx)

  def withNewRepoForTestingGenesisHash[A](testBody: GossipRepositoryInterpreter => A): Future[A] =
    withNewRepo(repo => IO(testBody(repo)))

  def withNewRepo[A](testBody: GossipRepositoryInterpreter => IO[A]): Future[A] = {

    def newMap: IO[swaydb.Map[Array[Byte], Array[Byte], IO]] = swaydb.memory.Map[Array[Byte], Array[Byte]]()

    for {
      ref <- Ref.of[IO, UInt256Bytes](UInt256Refine.EmptyBytes)
      db0 <- newMap
      db1 <- newMap
      db2 <- newMap
      newRepo = new GossipRepositoryInterpreter(ref, db0, db1, db2)
      result <- testBody(newRepo)
      _ <- db2.closeDatabase()
      _ <- db1.closeDatabase()
      _ <- db0.closeDatabase()
    } yield result
  }.toFuture

  val tests = Tests {

    test("genesis hash") {
      test("set and get genesis hash") - withNewRepoForTestingGenesisHash { repo =>
        repo.genesisHash = genesisHash
        assert(repo.genesisHash === genesisHash)
      }
    }

    test("transactions") {
      test("newTransactions from empty repository") - withNewRepo { repo =>
        repo.genesisHash = genesisHash
        for {
          newTransactions <- repo.newTransactions
        } yield {
          assert(newTransactions === Right(Set.empty))
        }
      }

      test("putNewTransaction and newTransactions") - withNewRepo { repo =>
        repo.genesisHash = genesisHash
        for {
          _ <- repo.putNewTransaction(signedTx)
          newTransactions <- repo.newTransactions
        } yield {
          println(s"===> new transactions: $newTransactions")
          assert(newTransactions === Right(Set(signedTx)))
        }
      }
    }
  }
}
