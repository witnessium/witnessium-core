package org.witnessium.core
package model

import java.time.Instant
import scodec.bits.ByteVector
import datatype.{BigNat, UInt256Bytes}


final case class TicketData(
  nonce             : Option[BigNat],
  photo             : Option[TicketData.PhotoMeta],
  owner             : Option[String],
  license           : Option[String],
  car               : Option[String],
  phone             : Option[String],
  violation         : Option[String],
  occuredAt         : Option[Instant],
  location          : Option[String],
  amount            : Option[BigNat],
  ticketTxHash      : Option[UInt256Bytes],
  payedAt           : Option[Instant],
  paymentDescription: Option[String],
)

object TicketData {

  final case class PhotoMeta(
    filename: String,
    contentType: String,
  )

  final case class Photo(
    meta: PhotoMeta,
    content: ByteVector,
  )
}
