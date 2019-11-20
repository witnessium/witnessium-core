package org.witnessium.core
package node
package store
package interpreter

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import scodec.bits.ByteVector
import swaydb.Map
import swaydb.data.{IO => SwayIO}

import codec.byte.{ByteCodec, ByteDecoder, ByteEncoder}

class SingleValueStoreSwayInterpreter[A: ByteCodec](map: Map[Array[Byte], Array[Byte], SwayIO]) extends SingleValueStore[IO, A] {

  def get(): EitherT[IO, String, Option[A]] = for {
    arrayOption <- EitherT.right(map.get(SingleValueStoreSwayInterpreter.Key).toIO)
    decodeResult <- arrayOption.traverse{ array =>
      EitherT.fromEither[IO](
        ByteDecoder[A].decode(ByteVector.view(array))
      )
    }
  } yield decodeResult.map(_.value)

  def put(a: A): EitherT[IO, String, Unit] = for{
    bytes <- EitherT.pure[IO, String](ByteEncoder[A].encode(a))
    _ <- EitherT.liftF[IO, String, Unit](map.put(SingleValueStoreSwayInterpreter.Key, bytes.toArray).toIO.map(_ => ()))
  } yield ()
}

object SingleValueStoreSwayInterpreter {
  private val Key: Array[Byte] = Array.fill(32)(0.toByte)
}
