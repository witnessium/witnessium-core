package org.witnessium.core
package node.crypto

import java.math.BigInteger
import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Interval
import org.bouncycastle.asn1.x9.X9IntegerConverter
import org.bouncycastle.math.ec.{ECAlgorithms, ECPoint}
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve
import UInt256Refine._

final case class Signature(v: Int Refined Signature.HeaderRange, r: UInt256BigInt, s: UInt256BigInt) {
  def signedMessageToKey(message: Array[Byte]): Either[String, BigInt] = {
    val header = v.value & 0xFF
    val messageHash = sha3(message)
    val recId = header - 27
    Signature
      .recoverFromSignature(recId, r, s, messageHash)
      .toRight("Could not recover public key from signature")
  }
}

object Signature {

  type HeaderRange = Interval.Closed[W.`27`.T, W.`34`.T]

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

