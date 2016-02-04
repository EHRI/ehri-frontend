package models

import auth.HashedPassword
import org.joda.time.DateTime

case class Account(
  id: String,
  email: String,
  verified: Boolean = false,
  staff: Boolean = false,
  active: Boolean = true,
  allowMessaging: Boolean = true,
  created: Option[DateTime] = None,
  lastLogin: Option[DateTime] = None,
  password: Option[HashedPassword] = None,
  isLegacy: Boolean = false
) {
  def hasPassword = password.isDefined
}