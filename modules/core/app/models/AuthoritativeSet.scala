package models

import base._

import models.base.Persistable
import defines.{ContentTypes, EntityType}
import models.json._
import play.api.i18n.Lang
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._


object AuthoritativeSetF {

  val NAME = "name"
  val DESCRIPTION = "description"

  import Entity._

  implicit val authoritativeSetWrites: Writes[AuthoritativeSetF] = new Writes[AuthoritativeSetF] {
    def writes(d: AuthoritativeSetF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          NAME -> d.name,
          DESCRIPTION -> d.description
        )
      )
    }
  }

  implicit val authoritativeSetReads: Reads[AuthoritativeSetF] = (
    (__ \ TYPE).readIfEquals(EntityType.AuthoritativeSet) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ IDENTIFIER).read[String] and
    (__ \ DATA \ NAME).readNullable[String] and
    (__ \ DATA \ DESCRIPTION).readNullable[String]
  )(AuthoritativeSetF.apply _)

  implicit val authoritativeSetFormat: Format[AuthoritativeSetF]
  = Format(authoritativeSetReads,authoritativeSetWrites)

  implicit object Converter extends RestConvertable[AuthoritativeSetF] with ClientConvertable[AuthoritativeSetF] {
    lazy val restFormat = authoritativeSetFormat
    lazy val clientFormat = Json.format[AuthoritativeSetF]
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

  private implicit val systemEventReads = SystemEvent.Converter.restReads
  private implicit val accessorReads = Accessor.Converter.restReads

  implicit val metaReads: Reads[AuthoritativeSet] = (
    __.read[AuthoritativeSetF] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).nullableListReads[Accessor] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).nullableHeadReads[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(AuthoritativeSet.apply _)

  implicit object Converter extends ClientConvertable[AuthoritativeSet] with RestReadable[AuthoritativeSet] {
    val restReads = metaReads

    val clientFormat: Format[AuthoritativeSet] = (
      __.format[AuthoritativeSetF](AuthoritativeSetF.Converter.clientFormat) and
      (__ \ "accessibleTo").nullableListFormat(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(AuthoritativeSet.apply _, unlift(AuthoritativeSet.unapply))
  }

  implicit object Resource extends RestResource[AuthoritativeSet] with RestContentType[AuthoritativeSet] {
    val entityType = EntityType.AuthoritativeSet
    val contentType = ContentTypes.AuthoritativeSet
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
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent],
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[AuthoritativeSetF]
  with Accessible
  with Holder[HistoricalAgent] {

  override def toStringLang(implicit lang: Lang): String = model.name.getOrElse(id)
}