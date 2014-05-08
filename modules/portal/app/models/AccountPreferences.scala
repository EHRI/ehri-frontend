package models

import play.api.data.Form
import play.api.data.Forms._

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class AccountPreferences(
  allowMessaging: Boolean
)

object AccountPreferences {
  def fromAccount(account: Account): AccountPreferences =
    new AccountPreferences(allowMessaging = account.allowMessaging)

  implicit def form: Form[AccountPreferences] = Form(
    mapping(
      "allowMessaging" -> boolean
    )(AccountPreferences.apply)(AccountPreferences.unapply)
  )
}
