package org.witnessium.core.node

import cats.data.NonEmptyList
import cats.effect.Sync
import io.finch.{Endpoint, EndpointModule}
import shapeless.HNil

trait ApiPath {
  def path: NonEmptyList[String]
  def toEndpoint[F[_]: Sync](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, HNil] = path.map(finch.path).reduceLeft(_ :: _)
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

  object ticket {
    object file extends ApiPath {
      override val path = NonEmptyList.of("ticket", "file")
    }
  }
}
