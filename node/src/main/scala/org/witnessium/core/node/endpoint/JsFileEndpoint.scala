package org.witnessium.core.node.endpoint

import cats.effect.Async
import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Future => TwitterFuture}
import io.finch._
import io.finch.internal.ToAsync

object JsFileEndpoint {

  type TwitterFutureToAsync[F[_]] =  ToAsync[TwitterFuture, F]

  def Get[F[_]: Async: TwitterFutureToAsync](implicit finch: EndpointModule[F]): Endpoint[F, Buf] = {
    import finch._

    get("resource" :: "js" :: path[String].withToString("{filename}")){ (filename: String) =>
      scribe.info(s"js request: '$filename'")
      Option(this.getClass.getResourceAsStream("/" + filename)).fold{
        TwitterFuture.value[Output[Buf]](NotFound(new Exception(s"Not found $filename")))
      }{ resourceStream => Reader.readAll(Reader.fromStream(resourceStream)).map(Ok) }
    }
  }
}
