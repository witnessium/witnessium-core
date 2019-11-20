package org.witnessium.core
package node
package repository

import cats.Monad
import cats.data.{EitherT, Kleisli}
import cats.effect.Sync
import cats.implicits._
import crypto.MerkleTrie
import crypto.MerkleTrie.{MerkleTrieState, NodeStore}
import datatype.{MerkleTrieNode, UInt256Bytes, UInt256Refine}
import model.{Address, Transaction}
import org.witnessium.core.datatype.MerkleTrieNode
import store.HashStore

object StateRepository {

  def put[F[_]: Monad](state: MerkleTrieState)(implicit store: HashStore[F, MerkleTrieNode]): EitherT[F, String, Unit] = {
    state.diff.addition.toList.traverse{ case (_, node) => store.put(node) }.map(_ => ())
  }

  implicit def nodeStoreFromHashStore[F[_]](implicit
    hashStore: HashStore[F, MerkleTrieNode]
  ): NodeStore[F] = Kleisli(hashStore.get)

  implicit class MerkleTrieStateOps(val merkleState: MerkleTrieState) {

    def getAll[F[_]:NodeStore:Sync]: EitherT[F, String, List[(Address, UInt256Bytes)]] = {
      import scodec.bits.BitVector
      for {
        iterant <- MerkleTrie.from[F, Unit](BitVector.empty).runA(merkleState)
        (bits: List[BitVector]) <- iterant.map(_._1).toListL
        stateTuple <- EitherT.fromEither[F](bits.traverse{ bit =>
          val (addressBit, hashBit) = bit.splitAt(20L * 8)
          for {
            address <- Address(addressBit.bytes)
            txHash <- UInt256Refine.from(hashBit.bytes)
          } yield (address, txHash)
        })
      } yield stateTuple
    }

    def get[F[_]:NodeStore:Sync](address: Address): EitherT[F, String, List[UInt256Bytes]] = {
      import scodec.bits.BitVector
      val addressBits: BitVector = address.bytes.bits
      for {
        iterant <- MerkleTrie.from[F, Unit](addressBits).runA(merkleState)
        (bits: List[BitVector]) <- iterant.map(_._1).takeWhile(_ startsWith addressBits).toListL
        hashBytes <- EitherT.fromEither[F](bits.traverse{ bit =>
          UInt256Refine.from(bit.drop(addressBits.size).bytes)
        })
      } yield hashBytes
    }

    def put[F[_]:NodeStore:Monad](address: Address, transaction: Transaction): EitherT[F, String, MerkleTrieState] = {
      scribe.info(s"Put address: $address, transaction: $transaction")
      val txBytes = crypto.hash(transaction)
      val program = for {
        _ <- transaction.inputs.toList.traverse{ txHash => MerkleTrie.removeByKey(address.bytes.bits ++ txHash.bits) }
        _ <- transaction.outputs.toList.traverse{ case (address1, _) => MerkleTrie.put((address1.bytes ++ txBytes).bits, ()) }
      } yield ()
      program runS merkleState
    }
  }
}
