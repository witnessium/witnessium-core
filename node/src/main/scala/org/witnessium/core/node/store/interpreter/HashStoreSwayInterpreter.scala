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
import crypto.Hash
import crypto.Hash.ops._
import datatype.UInt256Bytes
//import util.SwayIOCats._

class HashStoreSwayInterpreter[A: ByteCodec: Hash](map: Map[Array[Byte], Array[Byte], SwayIO]) extends HashStore[IO, A] {
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
    _ <- EitherT.liftF[IO, String, Unit](map.put(a.toHash.toArray, bytes.toArray).toIO.map(_ => ()))
  } yield ()
}
