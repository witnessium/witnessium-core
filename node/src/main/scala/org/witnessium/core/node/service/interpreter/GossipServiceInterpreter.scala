package org.witnessium.core
package node
package service
package interpreter

import cats.effect.IO
import model.NodeStatus

class GossipServiceInterpreter extends GossipService[IO] {

  import eu.timepit.refined.refineV
  import eu.timepit.refined.numeric.NonNegative
  import scodec.bits.ByteVector
  import datatype.{UInt256Bytes, UInt256Refine}
  import model.BigNat

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))//, "org.wartremover.warts.Nothing"))
  def nat(n: Int): BigNat = refineV[NonNegative](BigInt(n)).toOption.get

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))//, "org.wartremover.warts.Nothing"))
  def hexToUInt256Bytes(hex: String): UInt256Bytes = (for {
    bytes <- ByteVector.fromHex(hex)
    refined <- UInt256Refine.from(bytes).toOption
  } yield refined).get

  @SuppressWarnings(Array("org.wartremover.warts.Nothing"))//, "org.wartremover.warts.Nothing"))
  def status: IO[Either[String, NodeStatus]] = IO.pure(Right(NodeStatus(
    networkId = nat(1),
    genesisHash = hexToUInt256Bytes("0x8001a8a780ff6ebfe4ad0000bb7f807f7f01d2807f8056ffb3c700003a5000ff"),
    bestHash = hexToUInt256Bytes("0x80602b0aff00f9dc017f017f7fff75f06700637f29cd807f506c35d37fff7f80"),
    number = nat(1),
  )))
}
