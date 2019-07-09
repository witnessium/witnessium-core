package org.witnessium.core
package node.crypto

import java.security.{KeyPairGenerator, SecureRandom, Security}
import java.security.spec.ECGenParameterSpec
import java.util.Arrays
import eu.timepit.refined.refineV
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.{ECDSASigner, HMacDSAKCalculator}
import org.bouncycastle.jcajce.provider.asymmetric.ec.{BCECPrivateKey, BCECPublicKey}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.math.ec.{ECPoint, FixedPointCombMultiplier}

import datatype.UInt256Refine
import model.Signature

final case class KeyPair(privateKey: BigInt, publicKey: BigInt) {

  def sign(transactionHash: Array[Byte]): Either[String, Signature] = {
    val signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()))
    signer.init(true, new ECPrivateKeyParameters(privateKey.bigInteger, Curve))
    val Array(r, sValue) = signer.generateSignature(transactionHash)
    val s = if (BigInt(sValue) > HalfCurveOrder) Curve.getN subtract sValue else sValue
    for {
      r256 <- UInt256Refine.from(BigInt(r))
      s256 <- UInt256Refine.from(BigInt(s))
      recId <- (0 until 4).find { id => recoverFromSignature(id, r256, s256, transactionHash) === Some(publicKey) }.toRight {
        "Could not construct a recoverable key. The credentials might not be valid."
      }
      v <- refineV[Signature.HeaderRange](recId + 27)
    } yield Signature(v, r256, s256)
  }
}

object KeyPair {

  val secureRandom: SecureRandom = new SecureRandom()
  @SuppressWarnings(Array("org.wartremover.warts.AnyVal", "org.wartremover.warts.Null"))
  private val _ = if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) === null) {
    Security.addProvider(new BouncyCastleProvider())
  }

  def generate(): KeyPair = {
    val gen = KeyPairGenerator.getInstance("ECDSA", "BC")
    val spec = new ECGenParameterSpec("secp256k1")
    gen.initialize(spec, secureRandom)
    val pair = gen.generateKeyPair
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    val privateKey = BigInt(pair.getPrivate.asInstanceOf[BCECPrivateKey].getD)
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    val publicKey = BigInt(1, pair.getPublic.asInstanceOf[BCECPublicKey].getQ.getEncoded(false).tail)
    KeyPair(privateKey, publicKey)
  }

  def fromPrivate(privateKey: BigInt): KeyPair = {
    val point: ECPoint = new FixedPointCombMultiplier().multiply(Curve.getG, privateKey.bigInteger mod Curve.getN)
    val encoded: Array[Byte] = point.getEncoded(false)
    KeyPair(privateKey, BigInt(1, Arrays.copyOfRange(encoded, 1, encoded.length)))
  }
}
