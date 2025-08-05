package views.html

import play.api.i18n.Messages
import play.twirl.api.{Html, JavaScript}
import play.twirl.api.JavaScriptFormat.raw

package object attrs {
  final val _id = Symbol("id")
  final val _class = Symbol("class")
  final val _type = Symbol("type")
  final val _autocomplete = Symbol("autocomplete")
  final val _autofocus = Symbol("autofocus")
  final val _readonly = Symbol("readonly")
  final val _multiple = Symbol("multiple")
  final val _size = Symbol("size")
  final val _rows = Symbol("rows")
  final val _rel = Symbol("rel")
  final val _title = Symbol("title")
  final val _role = Symbol("role")
  final val _enctype = Symbol("enctype")
  final val _placeholder = Symbol("placeholder")
  final val _required = Symbol("required")
  final val _minlength = Symbol("minlength")
  final val _maxlength = Symbol("maxlength")
  final val _oninput = Symbol("oninput")
  final val _default = Symbol("_default")

  final val _autosubmit = Symbol("_autosubmit")
  final val _showconstraints = Symbol("_showConstraints")
  final val _select2 = Symbol("_select2")
  final val _hint = Symbol("_hint")

  final val _checked = Symbol("_checked")
  final val _disabled = Symbol("_disabled")
  final val _blank = Symbol("_blank")
  final val _hidden = Symbol("_hidden")
  final val _label = Symbol("_label")

  def passwordCustomValidity(passwordId: String = "password")(implicit messages: Messages): Html = {
    val msg = Messages("login.error.passwordsDoNotMatch")
    Html(s"this.setCustomValidity(this.value !== document.getElementById('$passwordId').value ? '$msg' : '')")
  }
}
