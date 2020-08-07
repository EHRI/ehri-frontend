package models

import forms.{HoneyPotForm, TimeCheckForm}

/**
 * Manage signup data.
 */
case class SignupData(
  name: String,
  email: String,
  password: String,
  confirm: String,
  allowMessaging: Boolean,
  timestamp: String,
  blankCheck: String,
  agreeTerms: Boolean
) extends TimeCheckForm with HoneyPotForm

object SignupData {
  val NAME = "name"
  val EMAIL = "email"
  val PASSWORD = "password"
  val CONFIRM = "confirm"
  val ALLOW_MESSAGING = "allowMessaging"
  val AGREE_TERMS = "agreeTerms"
}
