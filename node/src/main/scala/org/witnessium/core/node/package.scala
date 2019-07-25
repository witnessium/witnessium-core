package org.witnessium.core

import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Interval
import eu.timepit.refined.pureconfig._

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import scalatags.Text.TypedTag

import datatype.BigNat
import model.Address

package object node {
  type Html = TypedTag[String]

  type PortRange = Interval.Closed[W.`0`.T, W.`65535`.T]
  type Port = Int Refined PortRange

  implicit val mapReader: ConfigReader[Map[Address, BigNat]] = pureconfig.configurable.genericMapReader{
    str => Address.fromBase64(str).left.map(CannotConvert(str, "Address", _))
  }
}
