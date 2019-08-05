package org.witnessium.core.node
package util

import cats.~>
import cats.effect.Async
import io.finch.ToAsync
import scala.concurrent.{Future => ScalaFuture}
import swaydb.data.{IO => SwayIO}

trait AsyncConvert {

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  implicit def swayIoToAsync[E[_]: Async]: ToAsync[SwayIO, E] = {
    λ[SwayIO ~> ScalaFuture](_.toFuture) andThen implicitly[ToAsync[ScalaFuture, E]]
  }.asInstanceOf[ToAsync[SwayIO, E]]
}
