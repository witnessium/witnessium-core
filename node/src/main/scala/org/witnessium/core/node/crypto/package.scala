package org.witnessium.core
package node

import java.math.BigInteger
import org.bouncycastle.asn1.x9.{X9ECParameters, X9IntegerConverter}
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.math.ec.{ECAlgorithms, ECPoint}
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve
import org.bouncycastle.jcajce.provider.digest.Keccak

import UInt256Refine.UInt256BigInt
import model.Signature

package object crypto {

  val CurveParams: X9ECParameters = CustomNamedCurves.getByName("secp256k1")
  val Curve: ECDomainParameters = new ECDomainParameters(
    CurveParams.getCurve, CurveParams.getG, CurveParams.getN, CurveParams.getH)
  val HalfCurveOrder = BigInt(CurveParams.getN) / 2

  /** Keccak256 hash function
    *  @param input input byte array
    *  @return keccak256 hash bytes
    */
  def sha3(input: Array[Byte]): Array[Byte] = {
    val kecc = new Keccak.Digest256()
    kecc.update(input, 0, input.length)
    kecc.digest
  }

  implicit class SignatureOps(val signature: Signature) extends AnyVal {
    def signedMessageToKey(message: Array[Byte]): Either[String, BigInt] = {
      val header = signature.v.value & 0xFF
      val messageHash = sha3(message)
      val recId = header - 27
      recoverFromSignature(recId, signature.r, signature.s, messageHash)
        .toRight("Could not recover public key from signature")
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  private[crypto] def recoverFromSignature(recId: Int, r: UInt256BigInt, s: UInt256BigInt, message: Array[Byte]): Option[BigInt] = {
    require(recId >= 0, "recId must be positive")
    require(message != null, "message cannot be null")

    val n = Curve.getN
    val x = r.bigInteger add (n multiply BigInteger.valueOf(recId.toLong/2))
    val prime = SecP256K1Curve.q
    if (x.compareTo(prime) >= 0) None else {
      val R = {
        def decompressKey(xBN: BigInteger, yBit: Boolean): ECPoint = {
          val x9 = new X9IntegerConverter()
          val compEnc: Array[Byte] = x9.integerToBytes(xBN, 1 + x9.getByteLength(Curve.getCurve()))
          compEnc(0) = if (yBit) 0x03 else 0x02
          Curve.getCurve().decodePoint(compEnc)
        }
        decompressKey(x, (recId & 1) === 1)
      }
      if (!R.multiply(n).isInfinity()) None else {
        val e = new BigInteger(1, message)
        val eInv = BigInteger.ZERO subtract e mod n
        val rInv = r.bigInteger modInverse n
        val srInv = rInv multiply s.bigInteger mod n
        val eInvrInv = rInv multiply eInv mod n
        val q: ECPoint = ECAlgorithms.sumOfTwoMultiplies(Curve.getG(), eInvrInv, R, srInv)
        Some(BigInt(1, q.getEncoded(false).tail))
      }
    }
  }

}
