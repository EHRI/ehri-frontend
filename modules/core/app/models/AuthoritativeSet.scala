package models

import base._
import models.base.Persistable
import defines.{ContentTypes, EntityType}
import models.json._
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject
import services.data.{ContentType, Writable}


object AuthoritativeSetF {

  val NAME = "name"
  val DESCRIPTION = "description"

  import Entity._

  implicit val authoritativeSetFormat: Format[AuthoritativeSetF] = (
    (__ \ TYPE).formatIfEquals(EntityType.AuthoritativeSet) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ DATA \ NAME).formatNullable[String] and
    (__ \ DATA \ DESCRIPTION).formatNullable[String]
  )(AuthoritativeSetF.apply, unlift(AuthoritativeSetF.unapply))

  implicit object Converter extends Writable[AuthoritativeSetF] {
    lazy val restFormat: Format[AuthoritativeSetF] = authoritativeSetFormat
  }
}

case class AuthoritativeSetF(
  isA: EntityType.Value = EntityType.AuthoritativeSet,
  id: Option[String],
  identifier: String,
  name: Option[String],
  description: Option[String]
) extends Model with Persistable


object AuthoritativeSet {
  import AuthoritativeSetF._
  import Entity._
  import eu.ehri.project.definitions.Ontology._

  private implicit val systemEventReads = SystemEvent.SystemEventResource.restReads
  private implicit val accessorReads = Accessor.Converter.restReads

  implicit val metaReads: Reads[AuthoritativeSet] = (
    __.read[AuthoritativeSetF] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).readSeqOrEmpty[Accessor] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(AuthoritativeSet.apply _)

  implicit object AuthoritativeSetResource extends ContentType[AuthoritativeSet]  {
    val entityType = EntityType.AuthoritativeSet
    val contentType = ContentTypes.AuthoritativeSet
    val restReads: Reads[AuthoritativeSet] = metaReads
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.AuthoritativeSet),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> optional(nonEmptyText),
      DESCRIPTION -> optional(nonEmptyText)
    )(AuthoritativeSetF.apply)(AuthoritativeSetF.unapply)
  )
}


case class AuthoritativeSet(
  model: AuthoritativeSetF,
  accessors: Seq[Accessor] = Nil,
  latestEvent: Option[SystemEvent],
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[AuthoritativeSetF]
  with Accessible
  with Holder[HistoricalAgent] {

  override def toStringLang(implicit messages: Messages): String = model.name.getOrElse(id)
}