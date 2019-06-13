package org.witnessium.core.node.util

import com.twitter.io.Buf
import io.finch.{Encode, Text}
import scalatags.Text.TypedTag

trait ServingHtml {
  type Html = TypedTag[String]

  implicit val encodeHtml = Encode.instance[Html, Text.Html] { (html, cs) =>
    Buf.ByteArray.Owned(html.toString.getBytes(cs.name))
  }
}
