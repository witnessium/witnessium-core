package org.witnessium.core
package node
package store
package interpreter

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import scodec.bits.ByteVector
import swaydb.{IO => SwayIO, Map}

import codec.byte.{ByteCodec, ByteDecoder, ByteEncoder}
import crypto.Hash
import crypto.Hash.ops._
import datatype.UInt256Bytes

class HashStoreSwayInterpreter[A: ByteCodec: Hash](
  map: Map[Array[Byte], Array[Byte], Nothing, SwayIO.ApiIO]
) extends HashStore[IO, A] {
  def get(hash: UInt256Bytes): EitherT[IO, String, Option[A]] = for {
    arrayOption <- EitherT.right(map.get(hash.toBytes.toArray).toIO)
    decodeResult <- arrayOption.traverse{ array =>
      EitherT.fromEither[IO](
        ByteDecoder[A].decode(ByteVector.view(array))
      )
    }
  } yield decodeResult.map(_.value)

  def put(a: A): EitherT[IO, String, Unit] = for{
    bytes <- EitherT.pure[IO, String](ByteEncoder[A].encode(a))
    hash = a.toHash
    _ <- EitherT.liftF[IO, String, Unit](map.put(hash.toArray, bytes.toArray).toIO.map(_ => ()))

    arrayOption <- EitherT.right(map.get(hash.toBytes.toArray).toIO)
    decodeResult <- arrayOption.traverse{ array =>
      EitherT.fromEither[IO](
        ByteDecoder[A].decode(ByteVector.view(array))
      )
    }
    saved = decodeResult.map(_.value)
  } yield (saved match {
    case Some(aSaved) if aSaved === a => ()
    case _ =>
      scribe.warn(s"===> Put $a but saved $saved!!!")
      scribe.warn(s"===> put bytes: $bytes")
      scribe.warn(s"===> get bytes: ${arrayOption map ByteVector.view }")
      scribe.warn(s"===> decode result: $decodeResult")
  })
}
