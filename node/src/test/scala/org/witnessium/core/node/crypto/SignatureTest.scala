package org.witnessium.core
package node.crypto

import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import utest._

object SignatureTest extends TestSuite {

  override def utestAfterAll(): Unit = {
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
  }

  val tests = Tests {
    "signature" - {
      val keypair = KeyPair.generate()
      val msg = "public announcement"
      val hash = keccak256(msg.getBytes)
      val Right(signature) = keypair.sign(hash)

      val pubKeyFromSignature = signature.signedMessageToKey(msg.getBytes)
      val expected = Right(keypair.publicKey)
      assert(pubKeyFromSignature == expected)

    }
  }
}
