package org.witnessium.core
package node
package service
package interpreter

//import cats.effect.IO
import cats.Monad
import cats.data.{EitherT, StateT}
import cats.implicits._
import swaydb.data.{IO => SwayIO}
import crypto.MerkleTrie
import crypto.MerkleTrie.{MerkleTrieState, NodeStore}
import datatype.UInt256Bytes
import model.State
import repository.StateRepository
import util.SwayIOCats._
import StateServiceInterpreter._

class StateServiceInterpreter(val stateRepository: StateRepository[SwayIO]) extends StateService[SwayIO] {

  implicit val nodeStore: NodeStore[SwayIO] = stateRepository.getMerkleTrieNode

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def hash(newState: State): UInt256Bytes = (for {
    mtState <- stateToMerkleTrieState[SwayIO](newState) runS MerkleTrieState.empty
    rootHash <- EitherT.fromOption[SwayIO](mtState.root, s"Empty root: $mtState")
  } yield rootHash).value.map {
    case Right(hash) => hash
    case Left(msg) => throw new Exception(msg)
  }.get

  override def put(state: State): SwayIO[UInt256Bytes] = for {
    _ <- state.unused.toList.traverse { case (address, txHash) => stateRepository.put(address, txHash) }
  } yield hash(state)
}

object StateServiceInterpreter {
  def stateToMerkleTrieState[F[_]:NodeStore:Monad](
    state: State
  ): StateT[EitherT[F, String, *], MerkleTrieState, Unit] = state.unused.toList.traverse { case (address, txHash) =>
    MerkleTrie.put((address.bytes ++ txHash).bits, ())
  }.map(_ => ())
}
