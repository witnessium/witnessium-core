package org.witnessium.core
package node

import scala.util.Try
import io.finch.{DecodeEntity, DecodePath}
import scodec.bits.ByteVector
import datatype.{UInt256Bytes, UInt256Refine}
import model.Account

package object endpoint {

  implicit val uint256DecodePath: DecodePath[UInt256Bytes] = { s =>
    (for {
      bytes <- ByteVector.fromHexDescriptive(s)
      refined <- UInt256Refine.from(bytes)
    } yield refined).toOption
  }

  implicit val accountDecodePath: DecodePath[Account] = Account.from(_).toOption

  implicit val accountDecodeEntity: DecodeEntity[Account] = Account.from(_).left.map(new Exception(_))

  implicit val bigintDecodePath: DecodePath[BigInt] = { s => Try(BigInt(s)).toOption }

  implicit val bigintDecodeEntity: DecodeEntity[BigInt] = { s => Try(BigInt(s)).toEither }
}
