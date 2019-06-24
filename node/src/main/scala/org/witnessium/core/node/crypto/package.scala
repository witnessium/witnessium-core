package org.witnessium.core
package node

import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.jcajce.provider.digest.Keccak

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
}
