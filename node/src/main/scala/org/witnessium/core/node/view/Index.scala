package org.witnessium.core.node
package view

import scalatags.Text.all._
import scalatags.Text.tags2.{nav, title}
import util.VueTags._

object Index {
  val Title = "Witnessium Core Node"
  val AddNewAccount = "Add New Account"

  val ariaLabel = attr("aria-label")

  val skeleton = html(
    head(
      meta(charset:="utf-8"),
      meta(name:="viewport", content:="width=device-width, initial-scale=1"),
      title(Title),
      link(rel:="stylesheet", href:="https://cdnjs.cloudflare.com/ajax/libs/bulma/0.7.5/css/bulma.min.css"),
      script(defer, src:="https://use.fontawesome.com/releases/v5.3.1/js/all.js"),
      script(defer, src:="/resource/js/witnessium-core-js-jsdeps.min.js"),
      script(defer, src:="/resource/js/witnessium-core-js-fastopt.js"),
    ),
    body(
      div(id:="app")(
        nav(`class`:="navbar", role:="navigation", ariaLabel:="main navigation")(
          div(`class`:="navbar-brand")(
            h1(`class`:="title")(Title),
          ),
          div(`class`:="navbar-menu")(
            div(`class`:="navbar-start")(
              div(`class`:="navbar-item has-dropdown is-hoverable", vIf:="currentAccount != null")(
                a(`class`:="navbar-link")("{{ currentAccount }}"),
                div(`class`:="navbar-dropdown")(
                  a(`class`:="navbar-item", vFor:="account in accounts")("{{ account }}"),
                  hr(`class`:="navbar-divider"),
                  a(`class`:="navbar-item")(AddNewAccount),
                ),
              ),
              a(`class`:="navbar-item", vElse)(AddNewAccount),
            ),
            div(`class`:="navbar-end")(
            ),
          ),
        ),
      ),
      onload:= "window.vm = WitnessiumCoreJs.main();"
    ),
  )
}
