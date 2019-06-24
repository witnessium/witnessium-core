package org.witnessium.core.js

import scalajs.js
import scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scalajs.js.Dynamic.literal

@JSExportTopLevel("WitnessiumCoreJs")
object WitnessiumCoreJs {
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  @JSExport
  def main(): Vue = new Vue(literal(
    el = "#app",
    data = literal(
      currentAccount = "0xSOMEACCOUNT",
      accounts = js.Array("0xACCOUNT1", "0xACCOUNT2"),
    ),
  ))
}
