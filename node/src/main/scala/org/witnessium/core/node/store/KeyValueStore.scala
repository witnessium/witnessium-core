package org.witnessium.core
package node
package store

import cats.data.EitherT

trait KeyValueStore[F[_], K, V] {
  def get(key: K): EitherT[F, String, Option[V]]
  def put(key: K, value: V): F[Unit]
  def remove(key: K): F[Unit]
}
