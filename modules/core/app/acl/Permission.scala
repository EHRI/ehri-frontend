package acl

import defines.PermissionType

case class Permission(
  permission: PermissionType.Value,
  inheritedFrom: Option[String] = None) {
  override def toString = inheritedFrom.map { accessor =>
    s"$permission (from $accessor})"
  }.getOrElse(permission.toString)
}