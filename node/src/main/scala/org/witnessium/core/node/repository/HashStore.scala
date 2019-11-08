package org.witnessium.core
package node
package repository

import cats.data.EitherT
import datatype.UInt256Bytes

trait HashStore[F[_], A] {
  def get(hash: UInt256Bytes): EitherT[F, String, Option[A]]
  def put(a: A): EitherT[F, String, Unit]
  def remove(hash: UInt256Bytes): F[Unit]
}
