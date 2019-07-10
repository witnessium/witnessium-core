package org.witnessium.core.node

import cats.data.NonEmptyList
import cats.effect.IO
import io.finch.Endpoint
import io.finch.catsEffect
import shapeless.HNil

trait ApiPath {
  def path: NonEmptyList[String]
  def toEndpoint: Endpoint[IO, HNil] = path.map(catsEffect.path).reduceLeft(_ :: _)
  def toUrl: String = path.map("/" + _).toList.mkString
}

object ApiPath {
  object gossip {
    object status extends ApiPath {
      override val path = NonEmptyList.of("gossip", "status")
    }
  }
}
