package org.witnessium.core
package node

import io.finch.DecodePath
import scodec.bits.ByteVector
import datatype.{UInt256Bytes, UInt256Refine}

package object endpoint {

  implicit val uint256Decoder: DecodePath[UInt256Bytes] = { s =>
    (for {
      bytes <- ByteVector.fromHexDescriptive(s)
      refined <- UInt256Refine.from(bytes)
    } yield refined).toOption
  }
}
