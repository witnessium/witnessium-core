package org.witnessium.core
package node
package store
package interpreter

import cats.data.EitherT
import cats.implicits._
import scodec.bits.ByteVector
import swaydb.Map
import swaydb.data.{IO => SwayIO}

import codec.byte.{ByteCodec, ByteDecoder, ByteEncoder}
import util.SwayIOCats._

class SingleValueStoreSwayInterpreter[A: ByteCodec](map: Map[Array[Byte], Array[Byte], SwayIO]) extends SingleValueStore[SwayIO, A] {

  def get(): EitherT[SwayIO, String, Option[A]] = for {
    arrayOption <- EitherT.right(map.get(SingleValueStoreSwayInterpreter.Key))
    decodeResult <- arrayOption.traverse{ array =>
      EitherT.fromEither[SwayIO](
        ByteDecoder[A].decode(ByteVector.view(array))
      )
    }
  } yield decodeResult.map(_.value)

  def put(a: A): EitherT[SwayIO, String, Unit] = for{
    bytes <- EitherT.pure[SwayIO, String](ByteEncoder[A].encode(a))
    _ <- EitherT.liftF[SwayIO, String, Unit](map.put(SingleValueStoreSwayInterpreter.Key, bytes.toArray).map(_ => ()))
  } yield ()
}

object SingleValueStoreSwayInterpreter {
  private val Key: Array[Byte] = Array.fill(32)(0.toByte)
}
