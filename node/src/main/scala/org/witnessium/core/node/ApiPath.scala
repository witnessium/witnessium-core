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

    object bloomfilter extends ApiPath {
      override val path = NonEmptyList.of("gossip", "bloomfilter")
    }

    object unknownTransactions extends ApiPath {
      override val path = NonEmptyList.of("gossip", "unknownTransactions")
    }

    object state extends ApiPath {
      override val path = NonEmptyList.of("gossip", "state")
    }

    object block extends ApiPath {
      override val path = NonEmptyList.of("gossip", "block")
    }
  }
}
