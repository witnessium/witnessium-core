package org.witnessium.core
package node
package store
package interpreter

import java.nio.file.Path
import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import cats.implicits._
import scodec.bits.ByteVector
import swaydb.{IO => SwayIO, Map}
import swaydb.data.order.KeyOrder
import swaydb.data.slice.Slice
import swaydb.serializers.Default.ArraySerializer

import codec.byte.{ByteCodec, ByteDecoder, ByteEncoder, DecodeResult}
import datatype.BigNat

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter", "org.wartremover.warts.DefaultArguments"))
class StoreIndexSwayInterpreter[K: ByteCodec, V: ByteCodec](
  dir: Path,
  keyOrder: Either[KeyOrder[Slice[Byte]], KeyOrder[K]] = Left(KeyOrder.default),
)(implicit cs: ContextShift[IO]) extends StoreIndex[IO, K, V] {

  private val map: Map[K, Array[Byte], Nothing, SwayIO.ApiIO] = {
    implicit val _: Either[KeyOrder[Slice[Byte]], KeyOrder[K]] = keyOrder
    swaydb.persistent.Map[K, Array[Byte], Nothing, SwayIO.ApiIO](dir).get
  }

  def get(key: K): EitherT[IO, String, Option[V]] = for {
    arrayOption <- EitherT.right(map.get(key).toIO)
    decodeResult <- arrayOption.traverse{ array =>
      EitherT.fromEither[IO](
        ByteDecoder[V].decode(ByteVector.view(array))
      )
    }
  } yield decodeResult.map(_.value)

  def put(key: K, value: V): IO[Unit] = map.put(
    key,
    ByteEncoder[V].encode(value).toArray
  ).toIO.map(_ => ())

  def remove(key: K): IO[Unit] = map.remove(key).toIO.map(_ => ())

  def from(key: K, limit: Option[Int]): EitherT[IO, String, List[(K, V)]] = EitherT{
    val stream = map.fromOrAfter(key).stream
    val streamLimited = limit.fold(stream)(stream.take)

    streamLimited.materialize.toIO.map(
      _.toList.traverse { case (key, valueArray) =>
        for {
          valueDecoded <- (ByteDecoder[V].decode(ByteVector view valueArray))
          value <- StoreIndexSwayInterpreter.ensureNoRemainder(valueDecoded,
            s"Value bytes decoded with nonempty reminder: $valueDecoded"
          )
        } yield (key, value)
      }
    )
  }
}

object StoreIndexSwayInterpreter {

  def ensureNoRemainder[A](decoded: DecodeResult[A], msg: String): Either[String, A] =
    Either.cond(decoded.remainder.isEmpty, decoded.value, msg)

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  def reverseBignatStoreIndex[A: ByteCodec](dir: Path)(implicit
    cs: ContextShift[IO]
  ): StoreIndexSwayInterpreter[BigNat, A] =
    new StoreIndexSwayInterpreter[BigNat, A](dir, Left(KeyOrder.reverse))

}
