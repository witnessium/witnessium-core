package org.witnessium.core.node.crypto

import utest._

object KeyPairTest extends TestSuite {
  @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
  val tests = Tests {
    "fromPrivate" - {
      val keypair = KeyPair.generate()
      val fromPrivate = KeyPair.fromPrivate(keypair.privateKey)
      assert(fromPrivate == keypair)
    }
  }
}
