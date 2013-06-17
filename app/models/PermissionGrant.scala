package models

import base.{WrappedEntity, Accessor}
import org.joda.time.DateTime
import defines.{PermissionType,EntityType}


case class PermissionGrant(val e: Entity) extends WrappedEntity {

  val PERM_REL = "hasPermission"
  val ACCESSOR_REL = "hasAccessor"
  val TARGET_REL = "hasTarget"
  val GRANTEE_REL = "hasGrantee"
  val SCOPE_REL = "hasScope"

  // TODO: Put a timestamp on the model. This is here to remind me.
  lazy val timeStamp: DateTime = e.property("timestamp").flatMap(_.asOpt[String]).map(new DateTime(_))
    .getOrElse(sys.error("No timestamp found on action [%s]".format(e.id)))

  val permission: Option[PermissionType.Value] = e.relations(PERM_REL).headOption.map(e => PermissionType.withName(e.id))
  val accessor: Option[Accessor] = e.relations(ACCESSOR_REL).headOption.map(Accessor(_))
  val targets: List[ItemWithId] = e.relations(TARGET_REL).map { e => e.`type` match {
    // TODO: Handle content types differently to other types.
    case EntityType.ContentType => ItemWithId(e)
    case _ => ItemWithId(e)
  }

  }
  val grantee: Option[Accessor] = e.relations(GRANTEE_REL).headOption.map(Accessor(_))
  val scope: Option[ItemWithId] = e.relations(SCOPE_REL).headOption.map(ItemWithId(_))

  override def toString =
    "%s -> %s -> %s%s".format(
      accessor.getOrElse("?"), permission.getOrElse("?"),
      targets.headOption.getOrElse("?"), if(targets.length > 1) " ..." else "")

}

