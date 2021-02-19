package models

case class Permission(
  permission: PermissionType.Value,
  inheritedFrom: Option[String] = None) {
  override def toString: String = inheritedFrom.map { accessor =>
    s"$permission (from $accessor})"
  }.getOrElse(permission.toString)
}
