package org.witnessium.core
package node
package store

import cats.data.EitherT

trait StoreIndex[F[_], K, V] extends KeyValueStore[F, K, V] {
  def from(key: K, offset: Int, limit: Int): EitherT[F, String, List[(K, V)]]
}
