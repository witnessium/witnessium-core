package org.witnessium.core
package node

import codec.byte.ByteEncoder
import codec.circe._
import datatype._
import model._
import model.api._
import node.crypto._
import node.crypto.Hash.ops._
import cats.implicits._
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Method, Request, Response}
import com.twitter.util.Await
import eu.timepit.refined.refineV
import eu.timepit.refined.numeric.NonNegative
import io.circe.{Encoder, Json}
import io.circe.generic.auto._
import io.circe.refined._
import io.circe.parser.decode

object console {

  val client: Service[Request, Response] = Http.client.newService(s"localhost:8080")

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  val networkId: NetworkId = refineV[NonNegative](BigInt(101)).toOption.get

  def keyFromPrivate(privateKey: String): KeyPair = KeyPair.fromPrivate(BigInt(privateKey))

  private val txEncoder = ByteEncoder[Transaction]
  private val txJsonEncoer = Encoder[Transaction.Verifiable]

  implicit class KeyPairOps(val keyPair: KeyPair) {
    def address: Address = Address.fromPublicKey(keccak256)(keyPair.publicKey)

    def utxo: List[(UInt256Bytes, Long)] = {
      val request = Request(Method.Get, s"/address/utxo/$address")
      request.setContentTypeJson()
      val response = Await.result(client(request))
      val Right(addressInfo) = decode[AddressUtxoInfo](response.contentString)
      for {
        transaction <- addressInfo.transactions
        (address1, amount) <- transaction.value.outputs.toList if address1 === address
      } yield (transaction.toHash, amount.value.toLong)
    }

    def sendTo(to: List[(Address, Long)]): Transaction.Verifiable = {
      val (inputs, amounts) = utxo.unzip
      val balance = amounts.sum
      val outSum = to.map(_._2).sum
      val remainder = balance - outSum

      require(remainder > 0L, s"Not enough money: sending $outSum, but only have $balance")

      val Right(signed) = for{
        outputs <- ((address, remainder) :: to).traverse[Either[String, *], (Address, BigNat)]{
          case (address, amount) => refineV[NonNegative](BigInt(amount)).map((address, _))
        }
        tx: Transaction = Transaction(networkId, inputs.toSet, outputs.toSet)
        sig <- keyPair.sign(crypto.keccak256(txEncoder.encode(tx).toArray))
      } yield Signed(sig, tx)

      signed
    }
  }

  implicit class VerifiableTxOps(val vtx: Transaction.Verifiable) extends AnyVal {
    def toJson: Json = txJsonEncoer(vtx)
    def submit: String = {
      val request = Request(Method.Post, "/transaction")
      request.setContentString(toJson.toString)
      request.setContentTypeJson()
      val response = Await.result(client(request))
      response.contentString
    }
  }
}
