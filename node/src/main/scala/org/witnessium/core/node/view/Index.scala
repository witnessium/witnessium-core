package org.witnessium.core.node.view

import scalatags.Text.all._

object Index {
  val skeleton = html(
    head(
      meta(charset:="utf-8"),
      script(src:="/resource/js/witnessium-core-js-jsdeps.min.js"),
      script(src:="/resource/js/witnessium-core-js-fastopt.js"),
    ),
    body(
      h1("Witnessium Core Node"),
      div(id:= "contents"),
      onload:= "WitnessiumCoreJs.main(document.getElementById('contents'))",
    ),
  )
}
