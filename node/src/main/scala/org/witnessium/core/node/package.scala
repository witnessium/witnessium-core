package org.witnessium.core

import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Interval
import scalatags.Text.TypedTag

package object node {
  type Html = TypedTag[String]

  type Port = Int Refined Interval.Closed[W.`0`.T, W.`65535`.T]
}