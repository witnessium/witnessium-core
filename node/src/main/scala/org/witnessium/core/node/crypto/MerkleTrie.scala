package org.witnessium.core
package node
package crypto

import cats.Monad
import cats.data.{EitherT, StateT}
import cats.implicits._
import eu.timepit.refined.refineV
import eu.timepit.refined.api.Refined
import io.iteratee.Enumerator
import scodec.bits.BitVector
import shapeless.nat._16
import shapeless.syntax.sized._
import codec.byte.{ByteDecoder, ByteEncoder, DecodeResult}
import datatype.{MerkleTrieNode, UInt256Bytes}
import org.witnessium.core.util.refined.bitVector._

object MerkleTrie {
  
  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def get[F[_]:NodeStore:Monad, A: ByteDecoder](
    key: BitVector
  ): StateT[EitherT[F, String, *], MerkleTrieState, Option[A]] = StateT.inspectF{ (state: MerkleTrieState) =>
    if (state.root.isEmpty) EitherT.rightT[F, String](None) else for {
      node <- getNode(state)
      valueOption <- (node match {
        case MerkleTrieNode.Leaf(prefix, value) if key === prefix.value =>
          EitherT.fromEither[F](ByteDecoder[A].decode(value).flatMap{
            case DecodeResult(value, remainder) if remainder.isEmpty => Right(Some(value))
            case result => Left(s"Non empty remainder after decoding: $result")
          })
        case MerkleTrieNode.Branch(prefix, children) if (key startsWith prefix.value) && key.size >= prefix.value.size + 4 =>
          val (index, nextKey) = key.drop(prefix.value.size).splitAt(4)
          children.unsized(index.toInt(signed = false)) match {
            case Some(nextRoot) =>
              get(nextKey) runA state.copy(root = Some(nextRoot))
            case None =>
              EitherT.rightT[F, String](None)
          }
        case _ => EitherT.rightT[F, String](None)
      })
    } yield valueOption
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def put[F[_]:NodeStore:Monad, A: ByteEncoder](
    key: BitVector, value: A
  ): StateT[EitherT[F, String, *], MerkleTrieState, Unit] = StateT.modifyF((state: MerkleTrieState) => state.root match {
    case None =>
      val leaf =  MerkleTrieNode.Leaf(ensurePrefix(key), ByteEncoder[A].encode(value))
      val leafHash = hash[MerkleTrieNode](leaf)
      EitherT.rightT[F, String](state.copy(
        root = Some(leafHash),
        diff = state.diff.add(leafHash, leaf),
      ))
    case Some(root) =>
      getNode(state).flatMap{ (node: MerkleTrieNode) =>

        val prefix0: BitVector = node.prefix.value
        val commonPrefixNibbleSize: Int = (key ^ prefix0).grouped(4L).takeWhile(_ === BitVector.low(4L)).size
        val nextPrefixBitSize = ((key.size / 4L - 1L) min commonPrefixNibbleSize.toLong) * 4L
        val (commonPrefix, remainder1) = key splitAt nextPrefixBitSize
        val (index1, prefix1) = remainder1 splitAt 4L
        val remainder0 = prefix0 drop nextPrefixBitSize

        node match {
          case MerkleTrieNode.Leaf(_, value0) =>
            val (index00, prefix00) = remainder0 splitAt 4L
            val leaf0 = MerkleTrieNode.Leaf(ensurePrefix(prefix00), value0)
            val leaf0hash = hash[MerkleTrieNode](leaf0)
            val leaf1 = MerkleTrieNode.Leaf(ensurePrefix(prefix1), ByteEncoder[A].encode(value))
            val leaf1hash = hash[MerkleTrieNode](leaf1)
            val branch = MerkleTrieNode.Branch(ensurePrefix(commonPrefix),
              Vector.fill(16)(None)
                .updated(index00.toInt(signed = false), Some(leaf0hash))
                .updated(index1.toInt(signed = false), Some(leaf1hash))
                .ensureSized[_16]
            )
            val branchHash = hash[MerkleTrieNode](branch)
            EitherT.rightT[F, String](state.copy(
              root = Some(branchHash),
              diff = state.diff.add(branchHash, branch).add(leaf0hash, leaf0).add(leaf1hash, leaf1).remove(root),
            ))
          case MerkleTrieNode.Branch(_, children) if remainder0.isEmpty =>
            children.unsized(index1.toInt(signed = false)) match {
              case None =>
                val leaf1 = MerkleTrieNode.Leaf(ensurePrefix(prefix1), ByteEncoder[A].encode(value))
                val leaf1hash = hash[MerkleTrieNode](leaf1)
                val branch = MerkleTrieNode.Branch(ensurePrefix(commonPrefix),
                  children.unsized
                    .updated(index1.toInt(signed = false), Some(leaf1hash))
                    .ensureSized[_16]
                )
                val branchHash = hash[MerkleTrieNode](branch)
                EitherT.rightT[F, String](state.copy(
                  root = Some(branchHash),
                  diff = state.diff.add(branchHash, branch).add(leaf1hash, leaf1).remove(root),
                ))
              case Some(childHash) =>
                put(prefix1, value) runS state.copy(root = Some(childHash)) map { childState =>
                  val branch = MerkleTrieNode.Branch(node.prefix,
                    children.unsized
                      .updated(index1.toInt(signed = false), childState.root)
                      .ensureSized[_16]
                  )
                  val branchHash = hash[MerkleTrieNode](branch)
                  childState.copy(
                    root = Some(branchHash),
                    diff = childState.diff.add(branchHash, branch).remove(root)
                  )
                }
            }
          case MerkleTrieNode.Branch(_, children) =>
            val (index00, prefix00) = remainder0 splitAt 4L
            val branch00 = MerkleTrieNode.Branch(ensurePrefix(prefix00), children)
            val branch00hash = hash[MerkleTrieNode](branch00)
            val leaf1 = MerkleTrieNode.Leaf(ensurePrefix(prefix1), ByteEncoder[A].encode(value))
            val leaf1hash = hash[MerkleTrieNode](leaf1)
            val branch = MerkleTrieNode.Branch(ensurePrefix(commonPrefix),
              Vector.fill(16)(None)
                .updated(index00.toInt(signed = false), Some(branch00hash))
                .updated(index1.toInt(signed = false), Some(leaf1hash))
                .ensureSized[_16]
            )
            val branchHash = hash[MerkleTrieNode](branch)
            EitherT.rightT[F, String](state.copy(
              root = Some(branchHash),
              diff = state.diff.add(branchHash, branch).add(branch00hash, branch00).add(leaf1hash, leaf1).remove(root),
            ))
        }
      }
  })

  def remove[F[_]: NodeStore: Monad, A: ByteEncoder: ByteDecoder](value: A): StateT[EitherT[F, String, *], MerkleTrieState, Unit] =
    removeByKey(crypto.hash(value).bits)

  @SuppressWarnings(Array("org.wartremover.warts.Recursion","org.wartremover.warts.NonUnitStatements"))
  def removeByKey[F[_]: NodeStore: Monad, A: ByteDecoder](
    key: BitVector
  ): StateT[EitherT[F, String, *], MerkleTrieState, Unit] = StateT.modifyF((state: MerkleTrieState) => state.root match {
    case None => EitherT.leftT[F, MerkleTrieState](s"Fail to remove element from empty merkle trie: $state")
    case Some(root) => getNode(state).flatMap{
      case MerkleTrieNode.Leaf(prefix, _) if prefix.value === key =>
        EitherT.rightT[F, String](state.copy(
          root = None,
          diff = state.diff.remove(root),
        ))
      case MerkleTrieNode.Leaf(_, _) =>
        EitherT.leftT[F, MerkleTrieState](s"Fail to remove element: $key does not exist")
      case MerkleTrieNode.Branch(prefix, children) =>
        val commonPrefixNibbleSize: Int = (key ^ prefix.value).grouped(4L).takeWhile(_ === BitVector.low(4L)).size
        val nextPrefixBitSize = commonPrefixNibbleSize.toLong * 4L
        val remainder1 = key drop nextPrefixBitSize

        if (remainder1.size < 4L) EitherT.leftT[F, MerkleTrieState](s"Fail to remove element: $key does not exist")
        else {
          val (index1, key1) = remainder1 splitAt 4L
          children.unsized(index1.toInt(signed = false)) match {
            case None => EitherT.leftT[F, MerkleTrieState](s"Fail to remove element: $key does not exist")
            case Some(childHash) => removeByKey(key1) runS state.copy(root = Some(childHash)) flatMap {
              case childState if childState.root.isEmpty && children.unsized.count(_.nonEmpty) <= 1 =>
                EitherT.rightT[F, String](childState.copy(
                  root = None,
                  diff = childState.diff.remove(root),
                ))
              case childState =>
                val branch = MerkleTrieNode.Branch(prefix,
                  children.unsized
                    .updated(index1.toInt(signed = false), childState.root)
                    .ensureSized[_16]
                )
                val branchHash = hash[MerkleTrieNode](branch)

                compact runS state.copy(
                  root = Some(branchHash),
                  diff = childState.diff.add(branchHash, branch).remove(root),
                )
              }
          }
        }
    }
  })

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def from[F[_]:NodeStore:Monad, A: ByteDecoder](
    key: BitVector
  ): StateT[EitherT[F, String, *], MerkleTrieState, Enumerator[EitherT[F, String, *], (BitVector, A)]] = {
    StateT.inspectF((state: MerkleTrieState) => state.root match {
      case None => EitherT.rightT[F, String](Enumerator.empty)
      case Some(_) => getNode(state).flatMap{
        case MerkleTrieNode.Leaf(prefix, value) =>
          if (key <= prefix.value) EitherT.fromEither[F](ByteDecoder[A].decode(value).flatMap{
            case DecodeResult(v, remainder) if remainder.isEmpty => Right(Enumerator.enumOne((prefix.value, v)))
            case result => Left(s"Decoding failure: nonEmpty remainder $result")
          })
          else EitherT.rightT[F, String](Enumerator.empty)
        case MerkleTrieNode.Branch(prefix, children) =>

          def runFrom(key: BitVector)(
            hashWithIndex: (Option[UInt256Bytes], Int)
          ): EitherT[F, String, Enumerator[EitherT[F, String, *], (BitVector, A)]] = {
            from(key) runA state.copy(root = hashWithIndex._1) map (_.map{ case (key, a) =>
              (prefix.value ++ BitVector.fromInt(hashWithIndex._2, 4) ++ key, a)
            })
          }

          def flatten(
            enums: List[Enumerator[EitherT[F, String, *], (BitVector, A)]]
          ): Enumerator[EitherT[F, String, *], (BitVector, A)] = {
            (Enumerator.empty[EitherT[F, String, *], (BitVector, A)] /: enums)(_ append _)
          }

          if (key <= prefix.value) children.unsized.toList.zipWithIndex traverse runFrom(BitVector.empty) map flatten
          else if (!prefix.value.startsWith(key)) EitherT.rightT[F, String](Enumerator.empty)
          else {
            val (index1, key1) = key drop prefix.value.size splitAt 4L
            val targetChildren: List[(Option[UInt256Bytes], Int)] =
              children.unsized.toList.zipWithIndex.drop(index1.toInt(signed = false))
            targetChildren match {
              case Nil => EitherT.rightT[F, String](Enumerator.empty)
              case x :: xs =>
                for {
                  headList <- runFrom(key1)(x)
                  tailList <- xs traverse runFrom(BitVector.empty)
              } yield headList append flatten(tailList)
            }
          }
      }
    })
  }

  def getNode[F[_]:NodeStore:Monad](state: MerkleTrieState): EitherT[F, String, MerkleTrieNode] = for {
    root <- EitherT.fromOption[F](state.root, s"Cannot get node from empty merkle trie: $state")
    nodeOption <- EitherT.fromEither[F](Either.cond(!(state.diff.removal contains root),
      state.diff.addition.get(root),
      s"Merkle trie node is removed: $state"
    ))
    node <- nodeOption.fold[EitherT[F, String, MerkleTrieNode]]{
      EitherT(NodeStore[F].get(root)).subflatMap[String, MerkleTrieNode]{
        _.toRight(s"Merkle trie node is not found: $state")
      }
    }(EitherT.rightT[F, String](_))
  } yield node

  type PrefixBits = BitVector Refined MerkleTrieNode.PrefixCondition

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def ensurePrefix(bits: BitVector): PrefixBits = {
    refineV[MerkleTrieNode.PrefixCondition](bits).toOption.get
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def compact[F[_]: NodeStore: Monad, A]: StateT[EitherT[F, String, *], MerkleTrieState, Unit] = {
    StateT.modifyF((state: MerkleTrieState) => state.root match {
      case None => EitherT.rightT[F, String](state)
      case Some(root) => getNode(state).flatMap {
        case MerkleTrieNode.Branch(prefix, children) =>
          children.unsized.zipWithIndex.filter(_._1.nonEmpty) match {
            case Vector() =>
              EitherT.rightT[F, String](state.copy(
                root = None,
                diff = state.diff.remove(root),
              ))
            case Vector((Some(childHash), index)) =>
              def getPrefix(local: PrefixBits): PrefixBits = ensurePrefix(prefix.value ++ BitVector.fromInt(index, 4) ++ local.value)
              compact runS state.copy(root = Some(childHash)) flatMap { childState =>
                getNode(childState).map {
                  case MerkleTrieNode.Leaf(prefix0, value) =>
                    val nextLeaf = MerkleTrieNode.Leaf(getPrefix(prefix0), value)
                    val nextLeafHash = hash[MerkleTrieNode](nextLeaf)
                    state.copy(
                      root = Some(nextLeafHash),
                      diff = childState.diff.add(nextLeafHash, nextLeaf).remove(root),
                    )
                  case MerkleTrieNode.Branch(prefix0, children) =>
                    val nextBranch = MerkleTrieNode.Branch(getPrefix(prefix0), children)
                    val nextBranchHash = hash[MerkleTrieNode](nextBranch)
                    state.copy(
                      root = Some(nextBranchHash),
                      diff = childState.diff.add(nextBranchHash, nextBranch).remove(root),
                    )
                }
              }
            case _ =>
              EitherT.rightT[F, String](state)
          }
        case _ => EitherT.rightT[F, String](state)
      }
    })
  }

  implicit class BitVectorCompare(val bits: BitVector) extends AnyVal {
    def <=(that: BitVector): Boolean = {
      val xor = bits ^ that
      if (xor.populationCount === 0L) bits.size <= that.size
      else ~(bits implies that).populationCount === 0L
    }
  }

  trait NodeStore[F[_]] {
    def get(hash: UInt256Bytes): F[Either[String, Option[MerkleTrieNode]]]
  }

  object NodeStore {
    def apply[F[_]](implicit ns: NodeStore[F]): NodeStore[F] = ns
  }

  final case class MerkleTrieState(root: Option[UInt256Bytes], base: Option[UInt256Bytes], diff: MerkleTrieStateDiff)
  final case class MerkleTrieStateDiff(addition: Map[UInt256Bytes, MerkleTrieNode], removal: Set[UInt256Bytes]) {
    def add(hash: UInt256Bytes, node: MerkleTrieNode): MerkleTrieStateDiff = this.copy(addition = addition.updated(hash, node))
    def remove(hash: UInt256Bytes): MerkleTrieStateDiff =
      if (addition contains hash) this.copy(addition = addition - hash) else this.copy(removal = removal + hash)
  }
  object MerkleTrieState {
    val empty: MerkleTrieState = MerkleTrieState(None, None, MerkleTrieStateDiff(Map.empty, Set.empty))
  }
}
