package models

/**
  * Data about the available users and groups in the
  * system.
  *
  * @param users  a list of user IDs to names
  * @param groups a list of group IDs to names
  */
case class UsersAndGroups(
  users: Seq[(String, String)],
  groups: Seq[(String, String)]
)
