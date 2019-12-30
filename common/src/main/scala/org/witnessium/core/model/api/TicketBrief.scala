package org.witnessium.core
package model.api

import java.time.Instant
import datatype.{BigNat, UInt256Bytes}

final case class TicketBrief(
  tranHash : UInt256Bytes,
  violation: Option[String],
  occuredAt: Option[Instant],
  amount   : Option[BigNat],
  payedAt  : Option[Instant],
)
