package org.witnessium.core
package model

import java.time.Instant
import codec.byte.{ByteDecoder, ByteEncoder}

sealed trait MyGarageData

object MyGarageData {
  final case class Vehicle(
    vin: String,
    carNo: String,
    manufacturer: String,
    model: String,
    owner: String,
  ) extends MyGarageData

  final case class Part(
    name: String,
    partNo: String,
    manufacturer: String,
    date: Instant,
    warrenty: Instant,
    supplier: String,
    importer: String,
    seller: String,
    holder: String,
    updatedAt: String,
  ) extends MyGarageData

  implicit val mgdByteEncoder: ByteEncoder[MyGarageData] = {
    case v : Vehicle => 0.toByte +: ByteEncoder[Vehicle].encode(v)
    case p : Part => 1.toByte +: ByteEncoder[Part].encode(p)
  }

  implicit val mgdByteDecoder: ByteDecoder[MyGarageData] = ByteDecoder[Byte].flatMap {
    case 0 => ByteDecoder[Vehicle].widen[MyGarageData]
    case 1 => ByteDecoder[Part].widen[MyGarageData]
    case b => ByteDecoder.failedWithMessage[MyGarageData](s"Unknown MyGarageData distinguisher: $b")
  }

}
