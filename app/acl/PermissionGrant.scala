package acl

import defines.PermissionType
import models.base.Accessor

case class PermissionGrant[+T <: Accessor](
  val permission: PermissionType.Value,
  val inheritedFrom: Option[Accessor] = None) {
  override def toString = inheritedFrom match {
    case Some(accessor) => "%s (from %s)".format(permission, accessor.id)
    case None => permission.toString
  }
}