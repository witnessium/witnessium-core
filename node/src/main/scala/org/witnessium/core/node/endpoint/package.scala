package org.witnessium.core
package node

import io.circe._
import io.finch.DecodeEntity
import scodec.bits.ByteVector
import datatype.{UInt256Bytes, UInt256Refine}

package object endpoint {

  def encodeErrorList(es: List[Exception]): Json = {
    val messages = es.map(x => Json.fromString(x.getMessage))
    Json.obj("errors" -> Json.arr(messages: _*))
  }

  implicit val encodeException: Encoder[Exception] = Encoder.instance({
    case e: io.finch.Errors => encodeErrorList(e.errors.toList)
    case e: io.finch.Error =>
      e.getCause match {
        case e: io.circe.Errors => encodeErrorList(e.errors.toList)
        case _ => Json.obj("message" -> Json.fromString(e.getMessage))
      }
    case e: Exception => Json.obj("message" -> Json.fromString(e.getMessage))
  })

  implicit val uint256Decoder: DecodeEntity[UInt256Bytes] = DecodeEntity.instance[UInt256Bytes]{ s =>
    (for {
      bytes <- ByteVector.fromHexDescriptive(s)
      refined <- UInt256Refine.from(bytes)
    } yield refined).left.map(msg => new Exception(msg))
  }
}
