package org.witnessium.core.node.endpoint

import cats.effect.IO
import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Future => TwitterFuture}
import io.finch._
import io.finch.catsEffect._

class JsFileEndpoint {
  def Get: Endpoint[IO, Buf] = get("resource" :: "js" :: path[String].withToString("{filename}")) { (filename: String) =>
    scribe.info(s"js request: '$filename'")
    Option(getClass.getResourceAsStream("/" + filename)).fold{
      TwitterFuture.value[Output[Buf]](NotFound(new Exception(s"Not found $filename")))
    }{ resourceStream => Reader.readAll(Reader.fromStream(resourceStream)).map(Ok) }
  }
}
