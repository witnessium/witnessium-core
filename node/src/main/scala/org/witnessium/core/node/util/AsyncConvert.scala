package org.witnessium.core.node
package util

import cats.effect.Async
import io.finch.ToAsync
import scala.concurrent.{Future => ScalaFuture}
import swaydb.{IO => SwayIO}

trait AsyncConvert {

  implicit def swayIoToAsync[E[_]: Async]: ToAsync[SwayIO.ApiIO, E] = new ToAsync[SwayIO.ApiIO, E] {
    def apply[A](a: SwayIO.ApiIO[A]): E[A] = (implicitly[ToAsync[ScalaFuture, E]]).apply(a.toFuture)
  }
}
