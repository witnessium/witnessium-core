package org.witnessium.core.js

import scalatags.JsDom.all._
import org.scalajs.dom
import dom.html
import scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("WitnessiumCoreJs")
object WitnessiumCoreJs {
  @JSExport
  def main(container: html.Div) = {
    container.appendChild(
      div(
        p("Witnessium core js is called.")
      ).render
    )
  }
}
