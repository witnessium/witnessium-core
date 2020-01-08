package org.witnessium.core
package model.api

import java.time.Instant
import datatype.{BigNat, UInt256Bytes}

final case class TicketBrief(
  tranHash   : UInt256Bytes,
  offense    : Option[String],
  date       : Option[Instant],
  penalty    : Option[BigNat],
  paymentDate: Option[Instant],
)
