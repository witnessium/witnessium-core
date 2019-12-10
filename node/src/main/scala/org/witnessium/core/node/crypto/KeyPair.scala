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
import shapeless.syntax.typeable._

import datatype.{UInt256BigInt, UInt256Refine}
import model.Signature

final case class KeyPair(privateKey: UInt256BigInt, publicKey: PublicKey) {

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

  override def toString: String = s"KeyPair(${privateKey.toBytes}, $publicKey)"
}

object KeyPair {

  val secureRandom: SecureRandom = new SecureRandom()
  @SuppressWarnings(Array("org.wartremover.warts.AnyVal", "org.wartremover.warts.Null"))
  private val _ = if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) === null) {
    Security.addProvider(new BouncyCastleProvider())
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def generate(): KeyPair = {
    val gen = KeyPairGenerator.getInstance("ECDSA", "BC")
    val spec = new ECGenParameterSpec("secp256k1")
    gen.initialize(spec, secureRandom)
    val pair = gen.generateKeyPair
    (for {
      bcecPrivate <- pair.getPrivate.cast[BCECPrivateKey]
      bcecPublic <- pair.getPublic.cast[BCECPublicKey]
      privateKey <- UInt256Refine.from(BigInt(bcecPrivate.getD)).toOption
      publicKey <- PublicKey.fromByteArray(bcecPublic.getQ.getEncoded(false).tail).toOption
    } yield KeyPair(privateKey, publicKey)).getOrElse{
      throw new Exception(s"Wrong keypair result: $pair")
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def fromPrivate(privateKey: BigInt): KeyPair = {
    val point: ECPoint = new FixedPointCombMultiplier().multiply(Curve.getG, privateKey.bigInteger mod Curve.getN)
    val encoded: Array[Byte] = point.getEncoded(false)
    (for {
      private256 <- UInt256Refine.from(privateKey)
      public <- PublicKey.fromByteArray(Arrays.copyOfRange(encoded, 1, encoded.length))
    } yield KeyPair(private256, public)) match {
      case Right(keypair) => keypair
      case Left(msg) => throw new Exception(msg)
    }
  }
}
