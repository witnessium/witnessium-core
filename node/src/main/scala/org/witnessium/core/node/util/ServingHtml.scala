package org.witnessium.core.node
package util

import com.twitter.io.Buf
import io.finch.{Encode, Text}

trait ServingHtml {
  implicit val encodeHtml = Encode.instance[Html, Text.Html] { (html, cs) =>
    Buf.ByteArray.Owned(("<!DOCTYPE html>\n" ++ html.toString).getBytes(cs.name))
  }
}
