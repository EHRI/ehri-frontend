package models

import base.{WrappedEntity, Accessor}
import org.joda.time.DateTime

case class PermissionGrant(val e: Entity) extends WrappedEntity {

  val PERM_REL = "hasPermission"
  val ACCESSOR_REL = "hasAccessor"
  val TARGET_REL = "hasTarget"
  val GRANTEE_REL = "hasGrantee"
  val SCOPE_REL = "hasScope"

  // TODO: Put a timestamp on the model.
  val timeStamp: DateTime = e.property("timestamp").flatMap(_.asOpt[String]).map(new DateTime(_))
    .getOrElse(sys.error("No timestamp found on action [%s]".format(e.id)))

  val accessor: Option[Accessor] = e.relations(ACCESSOR_REL).headOption.map(Accessor(_))
  val targets: List[ItemWithId] = e.relations(TARGET_REL).map(ItemWithId(_))
  val grantee: Option[Accessor] = e.relations(GRANTEE_REL).headOption.map(Accessor(_))
  val scope: Option[ItemWithId] = e.relations(SCOPE_REL).headOption.map(ItemWithId(_))

}

