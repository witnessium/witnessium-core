package org.witnessium.core
package node
package repository

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import crypto.MerkleTrie
import crypto.MerkleTrie.{MerkleTrieState, NodeStore}
import datatype.{MerkleTrieNode, UInt256Bytes, UInt256Refine}
import model.{Address, Transaction}
import org.witnessium.core.datatype.MerkleTrieNode

trait StateRepository[F[_]] {

  def getMerkleTrieNode(merkleRoot: UInt256Bytes): F[Either[String, Option[MerkleTrieNode]]]

  def contains(address: Address, transactionHash: UInt256Bytes): F[Boolean]

  def get(address: Address): F[Either[String, Seq[UInt256Bytes]]]

  def put(address: Address, transactionHash: UInt256Bytes): F[Unit]

  def remove(address: Address, transactionHash: UInt256Bytes): F[Unit]

  def close(): F[Unit]

}

object StateRepository {

  final case class State(merkleState: MerkleTrieState) {
    def get[F[_]:NodeStore:Monad](address: Address): F[Either[String, Vector[UInt256Bytes]]] = {
      val addressBits = address.bytes.bits
      (MerkleTrie.from[F, Unit](addressBits) runA merkleState flatMap { enumerator =>
        enumerator.map(_._1).takeWhile(_ startsWith addressBits).toVector.flatMap(_.traverse{ bits =>
          EitherT.fromEither[F](UInt256Refine.from(bits.drop(addressBits.size).bytes))
        })
      }).value
    }

    def put[F[_]:NodeStore:Monad](address: Address, transaction: Transaction): F[Either[String, State]] = {
      val txBytes = crypto.hash(transaction)
      val program = for {
        _ <- transaction.inputs.toList.traverse{ txHash => MerkleTrie.remove(address.bytes ++ txHash) }
        _ <- transaction.outputs.toList.traverse { case (address1, _) => MerkleTrie.put((address1.bytes ++ txBytes).bits, ()) }
      } yield ()
      (program runS merkleState).map(State(_)).value
    }
  }

  def emptyState: State = State(MerkleTrieState.empty)
}
