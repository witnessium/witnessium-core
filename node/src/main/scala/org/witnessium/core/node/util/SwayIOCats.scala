package org.witnessium.core.node
package util

import cats.effect.{ExitCase, Sync}
import swaydb.data.IO

object SwayIOCats {

  implicit val syncSwayIO: Sync[IO] = new Sync[IO] {
    // Members declared in cats.Applicative
    override def pure[A](x: A): IO[A] = IO.Success(x)

    // Members declared in cats.ApplicativeError
    override def handleErrorWith[A](fa: IO[A])(f: Throwable => IO[A]): IO[A] = fa.recoverWith{
      case e: IO.Error => f(e.exception)
    }
    override def raiseError[A](e: Throwable): IO[A] = IO.Failure(e)

    // Members declared in cats.effect.Bracket
    override def bracketCase[A, B](acquire: IO[A])(use: A => IO[B])(
      release: (A, ExitCase[Throwable]) => IO[Unit]
    ): IO[B] = acquire.flatMap{ a =>
      val iob: IO[B] = for (b <- use(a); _ <- release(a, ExitCase.Completed)) yield b
      val _ = iob.onFailureSideEffect{ case IO.Failure(error) =>
        val _ = release(a, ExitCase.Error(error.exception)).asAsync.safeGetBlocking
      }
      iob
    }

    // Members declared in cats.FlatMap
    override def flatMap[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = fa flatMap f
    @annotation.tailrec
    override def tailRecM[A, B](a: A)(f: A => IO[Either[A,B]]): IO[B] = f(a) match {
      case IO.Failure(error) => IO.Failure[B](error)
      case IO.Success(Left(nextA)) => tailRecM(nextA)(f)
      case IO.Success(Right(b)) => IO.Success(b)
    }

    // Members declared in cats.effect.Sync
    override def suspend[A](thunk: => IO[A]): IO[A] = IO.Catch(thunk)
  }
}
