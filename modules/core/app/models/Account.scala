package models

import auth.HashedPassword
import org.joda.time.DateTime

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class Account(
  id: String,
  email: String,
  verified: Boolean,
  staff: Boolean = false,
  active: Boolean = true,
  allowMessaging: Boolean = true,
  lastLogin: Option[DateTime] = None,
  password: Option[HashedPassword] = None
) {
  def hasPassword: Boolean = password.isDefined
}