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
import datatype.MerkleTrieNode
import model.{Account, NameState}
import org.witnessium.core.datatype.MerkleTrieNode
import store.HashStore

object NameRepository {

  def put[F[_]: Monad](state: MerkleTrieState)(implicit
    store: HashStore[F, MerkleTrieNode]
  ): EitherT[F, String, Unit] = for {
    _ <- state.diff.addition.toList.traverse{ case (_, node) => store.put(node) }
    merkleTrieNodes <- state.diff.addition.toList.traverse{ case (hash, _) => store.get(hash).map((hash, _)) }
  } yield {
    scribe.debug(s"=== Names saved ===")
    merkleTrieNodes foreach { case (k, v) => scribe.debug(s"$k: $v") }
    scribe.debug(s"===================")
  }

  implicit def nodeStoreFromHashStore[F[_]](implicit
    hashStore: HashStore[F, MerkleTrieNode]
  ): NodeStore[F] = Kleisli(hashStore.get)

  implicit class MerkleTrieStateOps(val merkleState: MerkleTrieState) {

    def getAll[F[_]:NodeStore:Sync]: EitherT[F, String, List[(Account.Name, NameState)]] = {
      for {
        iterant <- MerkleTrie.from[F, Unit](BitVector.empty).runA(merkleState)
        (bits: List[BitVector]) <- iterant.map(_._1).toListL
        stateTuple <- EitherT.fromEither[F](bits.traverse{ bit =>
          ByteDecoder[(Account, NameState)].decode(bit.bytes).map(_.value).flatMap{
            case (Account.Named(name), nameState) => Right((name, nameState))
            case other => Left(s"No name: $other")
          }
        })
      } yield stateTuple
    }

    def get[F[_]:NodeStore:Sync](name: Account.Name): EitherT[F, String, Option[NameState]] = {
      val nameBits: BitVector = ByteEncoder[Account].encode(Account.Named(name)).bits
      for {
        nameStateOption <- MerkleTrie.get[F, NameState](nameBits).runA(merkleState)
      } yield nameStateOption
    }

    def put[F[_]:NodeStore:Monad](
      name: Account.Name, state: NameState
    ): EitherT[F, String, MerkleTrieState] = {
      scribe.info(s"Put name: $name, state: $state")
      val nameBits: BitVector = ByteEncoder[Account].encode(Account.Named(name)).bits
      val program = for {
        _ <- MerkleTrie.removeByKey(nameBits)
        _ <- MerkleTrie.put(nameBits, state)
      } yield ()
      program runS merkleState
    }
  }
}
