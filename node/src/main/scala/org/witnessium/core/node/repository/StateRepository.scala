package org.witnessium.core
package node
package repository

import cats.Monad
import cats.data.{EitherT, Kleisli}
import cats.effect.Sync
import cats.implicits._
import scodec.bits.BitVector

import codec.byte.{ByteDecoder, ByteEncoder}
import crypto.MerkleTrie
import crypto.MerkleTrie.{MerkleTrieState, NodeStore}
import datatype.{MerkleTrieNode, UInt256Bytes, UInt256Refine}
import model.{Account, Transaction}
import org.witnessium.core.datatype.MerkleTrieNode
import store.HashStore

object StateRepository {

  def put[F[_]: Monad](state: MerkleTrieState)(implicit
    store: HashStore[F, MerkleTrieNode]
  ): EitherT[F, String, Unit] = for {
    _ <- state.diff.addition.toList.traverse{ case (_, node) => store.put(node) }
    merkleTrieNodes <- state.diff.addition.toList.traverse{ case (hash, _) => store.get(hash).map((hash, _)) }
  } yield {
    scribe.debug(s"=== Nodes saved ===")
    merkleTrieNodes foreach { case (k, v) => scribe.debug(s"$k: $v") }
    scribe.debug(s"===================")
  }

  implicit def nodeStoreFromHashStore[F[_]](implicit
    hashStore: HashStore[F, MerkleTrieNode]
  ): NodeStore[F] = Kleisli(hashStore.get)

  implicit class MerkleTrieStateOps(val merkleState: MerkleTrieState) {

    def getAll[F[_]:NodeStore:Sync]: EitherT[F, String, List[(Account, UInt256Bytes)]] = {
      for {
        iterant <- MerkleTrie.from[F, Unit](BitVector.empty).runA(merkleState)
        (bits: List[BitVector]) <- iterant.map(_._1).toListL
        stateTuple <- EitherT.fromEither[F](bits.traverse{ bit =>
          ByteDecoder[(Account, UInt256Bytes)].decode(bit.bytes).map(_.value)
        })
      } yield stateTuple
    }

    def get[F[_]:NodeStore:Sync](account: Account): EitherT[F, String, List[UInt256Bytes]] = {
      val accoountBits: BitVector = ByteEncoder[Account].encode(account).bits
      for {
        iterant <- MerkleTrie.from[F, Unit](accoountBits).runA(merkleState)
        (bits: List[BitVector]) <- iterant.map(_._1).takeWhile(_ startsWith accoountBits).toListL
        hashBytes <- EitherT.fromEither[F](bits.traverse{ bit =>
          UInt256Refine.from(bit.drop(accoountBits.size).bytes)
        })
      } yield hashBytes
    }

    def put[F[_]:NodeStore:Monad](account: Account, transaction: Transaction): EitherT[F, String, MerkleTrieState] = {
      scribe.info(s"Put account: $account, transaction: $transaction")
      val accoountBits: BitVector = ByteEncoder[Account].encode(account).bits
      val txBytes = crypto.hash(transaction)
      val program = for {
        _ <- transaction.inputs.toList.traverse{
          txHash => MerkleTrie.removeByKey(accoountBits ++ txHash.bits)
        }
        _ <- transaction.outputs.toList.traverse{
          case (account1, _) => MerkleTrie.put((ByteEncoder[Account].encode(account1) ++ txBytes).bits, ())
        }
      } yield ()
      program runS merkleState
    }
  }
}
