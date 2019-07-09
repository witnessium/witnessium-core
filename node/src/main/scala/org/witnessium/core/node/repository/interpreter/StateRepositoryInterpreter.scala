package org.witnessium.core
package node.repository
package interpreter

import scala.concurrent.Future
import scodec.bits.ByteVector
import swaydb._
import swaydb.data.IO

import datatype.{UInt256Bytes, UInt256Refine}
import model.Address

class StateRepositoryInterpreter(swayMap: Map[Array[Byte], Array[Byte], IO]) extends StateRepository[Future] {

  def contains(address: Address, transactionHash: UInt256Bytes): Future[Boolean] =
    swayMap.contains((address.bytes ++ transactionHash).toArray).toFuture

  def get(address: Address): Future[Seq[UInt256Bytes]] = {
    val byteArray = address.bytes.toArray

    swayMap
      .keys
      .fromOrAfter(byteArray)
      .takeWhile(_ startsWith byteArray)
      .materialize
      .map{ addressAndTransactionHashes =>
        addressAndTransactionHashes.flatMap{ addressAndTransactionHash =>
          UInt256Refine.from(ByteVector.view(addressAndTransactionHash drop byteArray.size)).toOption.toList
        }
      }.toFuture
  }

  def put(address: Address, transactionHash: UInt256Bytes): Future[Unit] =
    swayMap.put(key = (address.bytes ++ transactionHash).toArray, value = Array.empty).map(_ => ()).toFuture

  def remove(address: Address, transactionHash: UInt256Bytes): Future[Unit] =
    swayMap.remove(key = (address.bytes ++ transactionHash).toArray).map(_ => ()).toFuture

}
