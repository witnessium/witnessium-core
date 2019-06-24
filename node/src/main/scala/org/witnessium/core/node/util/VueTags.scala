package org.witnessium.core.node.util

import scalatags.Text.all._

/**
  * ==Vue==
  * Utility class for making available the Vue.js directions in ScalaTags.
  * You can use this library as below.
  *
  * {{{
  * import VueTags._
  * }}}
  */
object VueTags {

  /**
    * Associate ScalaTags with `v-html` directive in Vue.js.
    *
    * ===Example:===
    *
    * {{{
    * div(
    *   vHtml := "html",
    *   "some text here"
    * )
    * }}}
    *
    * @see https://vuejs.org/v2/api/#v-html
    */
  lazy val vHtml: Attr = attr("v-html")

  /**
    * Associate ScalaTags with `v-bind:XXX` directive in Vue.js.
    *
    * ===Example:===
    *
    * {{{
    *   (img
    *     v-bind("src") := "imageSrc"
    *   )
    * }}}
    *
    * @see https://vuejs.org/v2/api/#v-bind
    * @param attrb bind html attributions
    * @return [[scalatags.generic.Attr]]
    */
  def vBind(attrb: String): Attr = attr(s"v-bind:$attrb")

  /**
    * Associate ScalaTags with `v-model` directive in Vue.js.
    *
    * ===Example:===
    *
    * {{{
    * (div
    *   v-model := "foo",
    *   "some text here"
    * )
    * }}}
    *
    * @see https://vuejs.org/v2/api/#v-model
    */
  lazy val vModel: Attr = attr("v-model")

  /**
    * Associate ScalaTags with `v-show` directive in Vue.js.
    *
    * ===Example:===
    *
    * {{{
    * (h1
    *   vShow := "ok",
    *   "Hello!"
    * )
    * }}}
    *
    * @see https://vuejs.org/v2/guide/conditional.html#v-show
    */
  lazy val vShow: Attr = attr("v-show")

  /**
    * Associate ScalaTags with `v-if` directive in Vue.js.
    *
    * ===Example:===
    *
    * {{{
    * (h1
    *   vIf := "ok",
    *   "Yes"
    * ),
    * (h1
    *   vElseIf := "maybe",
    *   "Maybe"
    * ),
    * (h1
    *   vElse,
    *   "No"
    * )
    * }}}
    *
    * @see https://vuejs.org/v2/guide/conditional.html
    */
  lazy val vIf: Attr = attr("v-if")

  /**
    * Associate ScalaTags with `v-else-if` directive in Vue.js.
    *
    * ===Example:===
    *
    * {{{
    * (h1
    *   vIf := "ok",
    *   "Yes"
    * ),
    * (h1
    *   vElseIf := "maybe",
    *   "Maybe"
    * ),
    * (h1
    *   vElse,
    *   "No"
    * )
    * }}}
    *
    * @see https://vuejs.org/v2/guide/conditional.html
    */
  lazy val vElseIf: Attr = attr("v-else-if")

  /**
    * Associate ScalaTags with `v-else` directive in Vue.js.
    *
    * ===Example:===
    *
    * {{{
    * (h1
    *   vIf := "ok",
    *   "Yes"
    * ),
    * (h1
    *   vElseIf := "maybe",
    *   "Maybe"
    * ),
    * (h1
    *   vElse,
    *   "No"
    * )
    * }}}
    *
    * @see https://vuejs.org/v2/guide/conditional.html
    */
  lazy val vElse: AttrPair = attr("v-else").empty

  /**
    * Associate ScalaTags with `v-for` directive in Vue.js.
    *
    * ===Example:===
    *
    * {{{
    * (div
    *   vFor := "item in items",
    *   "{{ item.text }}"
    * )
    * }}}
    *
    * @see https://vuejs.org/v2/api/#v-for
    */
  lazy val vFor: Attr = attr("v-for")

  /**
    * Associate ScalaTags with `v-on` directive in Vue.js.
    *
    * ===Example:===
    *
    * {{{
    * (button
    *   vOn := "doThis"
    * )
    * }}}
    *
    *
    * @see https://vuejs.org/v2/api/#v-on
    */
  lazy val vOn: Attr = attr("v-on")

  /**
    * Associate ScalaTags with `v-on` directive in Vue.js.
    *
    * ===Example:===
    *
    * ====click event binding====
    * {{{
    * (button
    *   vOn("click") := "doThis"
    * )
    * }}}
    *
    * @see https://vuejs.org/v2/api/#v-on
    * @param event event name
    * @return [[scalatags.generic.Attr]]
    */

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def vOn(event: String): Attr = attr(s"v-on:$event")

  /**
    * Associate ScalaTags with `v-pre` directive in Vue.js.
    *
    * ===Example:===
    *
    * {{{
    * (span
    *   v-pre,
    *   "{{ this will not be compiled }}"
    * )
    * }}}
    *
    * @see https://vuejs.org/v2/api/#v-pre
    */
  lazy val vPre: AttrPair = attr("v-pre").empty

  /**
    * Associate ScalaTags with `v-cloak` directive in Vue.js.
    *
    * ===Example:===
    *
    * {{{
    * (div
    *   v-cloak,
    *   "{{ message }}"
    * )
    * }}}
    *
    * @see https://vuejs.org/v2/api/#v-cloak
    */
  lazy val vCloak: AttrPair = attr("v-cloak").empty

  /**
    * Associate ScalaTags with `v-once` directive in Vue.js.
    *
    * ===Example:===
    *
    * {{{
    * (span
    *   vOnce,
    *   "This will never change: {{ msg }}"
    * ),
    * (div
    *   vOnce,
    *   (h1
    *     "comment"
    *   ),
    *   (p
    *     "{{ msg }}"
    *   )
    * ),
    * (ul
    *   (li
    *     vFor := "i in list",
    *     vOnce,
    *     "{{ i }}"
    *   )
    * )
    * }}}
    *
    * @see https://vuejs.org/v2/api/#v-once
    */
  lazy val vOnce: AttrPair = attr("v-once").empty
}
