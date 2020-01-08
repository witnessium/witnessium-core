package org.witnessium.core
package model

import java.time.Instant
import scodec.bits.ByteVector
import datatype.{BigNat, UInt256Bytes}


final case class TicketData(
  nonce       : Option[BigNat],
  footage     : Option[TicketData.FootageMeta],
  driverName  : Option[String],
  licenseNo   : Option[String],
  plateNo     : Option[String],
  contactInfo : Option[String],
  offense     : Option[String],
  location    : Option[String],
  date        : Option[Instant],
  penalty     : Option[BigNat],
  ticketTxHash: Option[UInt256Bytes],
  paymentDate : Option[Instant],
  paymentType : Option[String],
)

object TicketData {

  final case class FootageMeta(
    filename: String,
    contentType: String,
  )

  final case class Footage(
    meta: FootageMeta,
    content: ByteVector,
  )
}
