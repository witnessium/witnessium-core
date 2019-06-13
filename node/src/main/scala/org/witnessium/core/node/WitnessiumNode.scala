package org.witnessium.core.node

import cats.effect.IO
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import io.finch._
import io.finch.catsEffect._
//import io.finch.circe._
//import io.circe.generic.auto._

import endpoint.JsFileEndpoint
import util.ServingHtml
import view.Index

object WitnessiumNode extends TwitterServer with ServingHtml {

  val index: Endpoint[IO, Html] = get(pathEmpty) { Ok(Index.skeleton) }

  val jsFileEndpoint: JsFileEndpoint = new JsFileEndpoint()

  lazy val api: Service[Request, Response] = Bootstrap
    .serve[Text.Html](index)
    .serve[Application.Javascript](jsFileEndpoint())
    .toService

  def main(): Unit = {
    try {
      val server = Http.server.serve(":8081", api)
      onExit {
        server.close()
        ()
      }
      Await.ready(server)
      ()
    } catch {
      case _: java.lang.InterruptedException =>
        scribe.info("Server execution is interrupted.")
      case e: Exception =>
        scribe.error(e)
        e.printStackTrace()
    }
  }
}
