package org.witnessium.core.node
package util

import cats.effect.Async
import io.finch.ToAsync
import scala.concurrent.{Future => ScalaFuture}
import swaydb.data.{IO => SwayIO}

trait AsyncConvert {

  implicit def swayIoToAsync[E[_]: Async]: ToAsync[SwayIO, E] = new ToAsync[SwayIO, E] {
    def apply[A](a: SwayIO[A]): E[A] = (implicitly[ToAsync[ScalaFuture, E]]).apply(a.toFuture)
  }
}
