package models

import eu.ehri.project.definitions.Ontology
import forms.mappings.optionalText
import models.json._
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject


object AuthoritativeSetF {

  val NAME = "name"
  val DESCRIPTION = "description"
  val ALLOW_PUBLIC = Ontology.IS_PROMOTABLE

  val FIELDS: Seq[(String, Seq[String])] = Seq("_" -> Seq(NAME, DESCRIPTION))

  import Entity._

  implicit lazy val _format: Format[AuthoritativeSetF] = (
    (__ \ TYPE).formatIfEquals(EntityType.AuthoritativeSet) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ DATA \ NAME).formatNullable[String] and
    (__ \ DATA \ DESCRIPTION).formatNullable[String] and
    (__ \ DATA \ ALLOW_PUBLIC).formatWithDefault(false)
  )(AuthoritativeSetF.apply, unlift(AuthoritativeSetF.unapply))

  implicit object Converter extends Writable[AuthoritativeSetF] {
    lazy val _format: Format[AuthoritativeSetF] = AuthoritativeSetF._format
  }
}

case class AuthoritativeSetF(
  isA: EntityType.Value = EntityType.AuthoritativeSet,
  id: Option[String],
  identifier: String,
  name: Option[String],
  description: Option[String],
  isPromotable: Boolean = false
) extends ModelData with Persistable


object AuthoritativeSet {
  import AuthoritativeSetF._
  import Entity._
  import eu.ehri.project.definitions.Ontology._

  implicit lazy val _reads: Reads[AuthoritativeSet] = (
    __.read[AuthoritativeSetF] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).readSeqOrEmpty[Accessor] and
    (__ \ RELATIONSHIPS \ PROMOTED_BY).readSeqOrEmpty[UserProfile] and
    (__ \ RELATIONSHIPS \ DEMOTED_BY).readSeqOrEmpty[UserProfile] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(AuthoritativeSet.apply _)

  implicit object AuthoritativeSetResource extends ContentType[AuthoritativeSet]  {
    val entityType = EntityType.AuthoritativeSet
    val contentType = ContentTypes.AuthoritativeSet
    val _reads: Reads[AuthoritativeSet] = AuthoritativeSet._reads
  }

  val form: Form[AuthoritativeSetF] = Form(
    mapping(
      ISA -> ignored(EntityType.AuthoritativeSet),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> optional(nonEmptyText),
      DESCRIPTION -> optionalText,
      ALLOW_PUBLIC -> default(boolean, true)
    )(AuthoritativeSetF.apply)(AuthoritativeSetF.unapply)
  )
}


case class AuthoritativeSet(
  data: AuthoritativeSetF,
  accessors: Seq[Accessor] = Nil,
  promoters: Seq[UserProfile] = Nil,
  demoters: Seq[UserProfile] = Nil,
  latestEvent: Option[SystemEvent],
  meta: JsObject = JsObject(Seq())
) extends Model
  with Accessible
  with Promotable
  with Holder[HistoricalAgent] {

  type T = AuthoritativeSetF

  override def isPromotable: Boolean = data.isPromotable

  override def toStringLang(implicit messages: Messages): String = data.name.getOrElse(id)
}
