package models.view

import models.UserProfile

case class UserDetails(
  userOpt: Option[UserProfile] = None,
  watchedItems: Seq[String] = Nil
)
