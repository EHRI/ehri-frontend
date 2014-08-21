package models.view

import models.UserProfile

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class UserDetails(
  userOpt: Option[UserProfile] = None,
  watchedItems: Seq[String] = Nil
)
