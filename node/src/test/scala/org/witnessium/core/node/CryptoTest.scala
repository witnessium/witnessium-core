package org.witnessium.core.node

import scodec.bits.ByteVector
import utest._

object CryptoTest extends TestSuite {
  val tests = Tests {
    test("sha3"){
      val input = "hello"
      val expected = "1c8aff950685c2ed4bc3174f3472287b56d9517b9c948127319a09a7a36deac8" // keccak256 result from "hello"

      assert(ByteVector(crypto.keccak256(input.getBytes())).toHex == expected)
    }
  }
}
