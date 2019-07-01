package org.witnessium.core.node.crypto

import utest._

object KeyPairTest extends TestSuite {
  val tests = Tests {
    test("fromPrivate"){
      val keypair = KeyPair.generate()
      val fromPrivate = KeyPair.fromPrivate(keypair.privateKey)
      assert(fromPrivate == keypair)
    }
  }
}
