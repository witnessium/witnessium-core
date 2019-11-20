package org.witnessium.core
package node
package store

import cats.data.EitherT

trait SingleValueStore[F[_], A] {
  def get(): EitherT[F, String, Option[A]]
  def put(a: A): EitherT[F, String, Unit]
}
