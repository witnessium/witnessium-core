package org.witnessium.core.node
package util

import cats.Monad
import swaydb.data.IO

object SwayIOCats {

  implicit val monadSwayIO: Monad[IO] = new Monad[IO] {
    // Members declared in cats.Applicative
    override def pure[A](x: A): IO[A] = IO.Success(x)

    // Members declared in cats.FlatMap
    override def flatMap[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = fa flatMap f
    @annotation.tailrec
    override def tailRecM[A, B](a: A)(f: A => IO[Either[A,B]]): IO[B] = f(a) match {
      case IO.Failure(error) => IO.Failure[B](error)
      case IO.Success(Left(nextA)) => tailRecM(nextA)(f)
      case IO.Success(Right(b)) => IO.Success(b)
    }

    // Members declared in cats.Functor
    override def map[A, B](fa: IO[A])(f: A => B): IO[B] = fa map f
  }
}
