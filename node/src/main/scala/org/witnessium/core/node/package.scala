package org.witnessium.core

import java.time.Instant
import java.time.format.DateTimeFormatter
import cats.effect.IO
import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Interval
import eu.timepit.refined.pureconfig._
import io.finch.ToAsync
import pureconfig.{ConfigConvert, ConfigReader}
import pureconfig.error.CannotConvert
import scalatags.Text.TypedTag

import datatype.BigNat
import model.Address

package object node extends util.AsyncConvert {
  type Html = TypedTag[String]

  type PortRange = Interval.Closed[W.`0`.T, W.`65535`.T]
  type Port = Int Refined PortRange

  implicit val mapReader: ConfigReader[Map[Address, BigNat]] = pureconfig.configurable.genericMapReader{
    str => Address.fromBase64(str).left.map(CannotConvert(str, "Address", _))
  }

  implicit class AsyncOps[F[_], A](val a: F[A]) extends AnyVal {
    def toIO(implicit toAsync: ToAsync[F, IO]): IO[A] = toAsync(a)
  }

  implicit val instantConvert: ConfigConvert[Instant] = {
    ConfigConvert.viaNonEmptyString[Instant](
      ConfigConvert.catchReadError(Instant.parse),
      DateTimeFormatter.ISO_INSTANT.format
    )
  }
}
