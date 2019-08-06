package org.witnessium.core
package node.repository
package interpreter

import cats.implicits._
import scodec.bits.ByteVector
import swaydb._
import swaydb.data.IO

import datatype.{UInt256Bytes, UInt256Refine}
import model.Address

class StateRepositoryInterpreter(swayMap: Map[Array[Byte], Array[Byte], IO]) extends StateRepository[IO] {

  def contains(address: Address, transactionHash: UInt256Bytes): IO[Boolean] =
    swayMap.contains((address.bytes ++ transactionHash).toArray)

  def get(address: Address): IO[Either[String, Seq[UInt256Bytes]]] = {
    val byteArray = address.bytes.toArray

    swayMap
      .keys
      .fromOrAfter(byteArray)
      .takeWhile(_ startsWith byteArray)
      .materialize
      .map{ addressAndTransactionHashes =>
        addressAndTransactionHashes.toList.traverse{ addressAndTransactionHash =>
          UInt256Refine.from(ByteVector.view(addressAndTransactionHash drop byteArray.size))
        }
      }
  }

  def put(address: Address, transactionHash: UInt256Bytes): IO[Unit] =
    swayMap.put(key = (address.bytes ++ transactionHash).toArray, value = Array.empty).map(_ => ())

  def remove(address: Address, transactionHash: UInt256Bytes): IO[Unit] =
    swayMap.remove(key = (address.bytes ++ transactionHash).toArray).map(_ => ())

  def close(): IO[Unit] = swayMap.closeDatabase()

}
