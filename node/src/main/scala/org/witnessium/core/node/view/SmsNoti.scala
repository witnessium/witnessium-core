package org.witnessium.core.node
package view

import scalacss.DevDefaults._
import scalacss.ScalatagsCss._
import scalatags.Text.TypedTag

object SmsNoti {
  val Title = "Notification SMS"

  object LocalStyle extends StyleSheet.Inline {
    import dsl._

    private[view] val body = style(
      width(100.%%),
      font := "'Malgun Gothic', serif",
    )

    private[view] val bg = style(
      background := "no-repeat url('/resource/img/mobilebgs-3.gif')",
      width(450.px),
      height(1080.px),
      margin(0.px, auto),
    )

    private[view] val section01 = style(
      position.relative,
      width(200.px),
      height(143.px),
      marginLeft(60.px),
      top(200.px),
      fontSize(16.px),
      fontWeight._500,
    )

    private[view] val info = style(
      position.relative,
      maxHeight(130.px),
      maxWidth(200.px),
      border(1.px, solid, c"#999"),
      top(5.px),
      marginBottom(10.px),
    )
  }

  import scalatags.Text.all._
  import scalatags.Text.tags2.title

  def render(name: String, imagePath: String, link: String): Html = html(
    LocalStyle.render[TypedTag[String]],
    head(
      meta(charset:="utf-8"),
      title(Title),
    ),
    body(
      LocalStyle.body,
      div(
        LocalStyle.bg,
        div(
          LocalStyle.section01,
          div(
            p(s"$name!"),
            p(s"Ticket has been issued for vilation of traffic requlations."),
            p(img(
              LocalStyle.info,
              src:=imagePath
            )),
            a(href:=link)("http://mot.gov.cn/" + link.drop(30).take(6)),
          ),
        ),
      ),
    ),
  )
}
