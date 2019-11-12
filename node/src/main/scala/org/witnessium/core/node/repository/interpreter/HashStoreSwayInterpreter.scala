package org.witnessium.core
package node
package repository
package interpreter

import cats.data.EitherT
import cats.implicits._
import scodec.bits.ByteVector
import swaydb.Map
import swaydb.data.{IO => SwayIO}

import codec.byte.{ByteCodec, ByteDecoder, ByteEncoder}
import crypto.Hash
import crypto.Hash.ops._
import datatype.UInt256Bytes
import util.SwayIOCats._

class HashStoreSwayInterpreter[A: ByteCodec: Hash](map: Map[Array[Byte], Array[Byte], SwayIO]) extends HashStore[SwayIO, A] {
  def get(hash: UInt256Bytes): EitherT[SwayIO, String, Option[A]] = for {
    arrayOption <- EitherT.right(map.get(hash.toBytes.toArray))
    decodeResult <- arrayOption.traverse{ array =>
      EitherT.fromEither[SwayIO](
        ByteDecoder[A].decode(ByteVector.view(array))
      )
    }
  } yield decodeResult.map(_.value)

  def put(a: A): EitherT[SwayIO, String, Unit] = for{
    bytes <- EitherT.pure[SwayIO, String](ByteEncoder[A].encode(a))
    _ <- EitherT.liftF[SwayIO, String, Unit](map.put(a.toHash.toArray, bytes.toArray).map(_ => ()))
  } yield ()
}
