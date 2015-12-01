package models

import base._

import models.base.Persistable
import defines.{ContentTypes, EntityType}
import play.api.libs.json._
import models.json._
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._
import eu.ehri.project.definitions.Ontology
import backend._
import play.api.libs.json.JsObject


object VocabularyType extends Enumeration {
  type Type = Value
}

object VocabularyF {
  val NAME = "name"
  val DESCRIPTION = "description"

  import Entity._

  implicit val vocabularyFormat: Format[VocabularyF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Vocabulary) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ DATA \ NAME).formatNullable[String] and
    (__ \ DATA \ DESCRIPTION).formatNullable[String]
  )(VocabularyF.apply _, unlift(VocabularyF.unapply))

  implicit object Converter extends Writable[VocabularyF] {
    lazy val restFormat = vocabularyFormat
  }
}


case class VocabularyF(
  isA: EntityType.Value = EntityType.Vocabulary,
  id: Option[String],
  identifier: String,
  name: Option[String],
  description: Option[String]
) extends Model with Persistable


object Vocabulary {
  import VocabularyF._
  import Entity._
  import Ontology._

  private implicit val systemEventReads = SystemEvent.SystemEventResource.restReads

  implicit val metaReads: Reads[Vocabulary] = (
    __.read[VocabularyF] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadSeqOrEmpty(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Vocabulary.apply _)

  implicit object VocabularyResource extends backend.ContentType[Vocabulary]  {
    val entityType = EntityType.Vocabulary
    val contentType = ContentTypes.Vocabulary
    val restReads = metaReads
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.Vocabulary),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> optional(nonEmptyText),
      DESCRIPTION -> optional(nonEmptyText)
    )(VocabularyF.apply)(VocabularyF.unapply)
  )
}


case class Vocabulary(
  model: VocabularyF,
  accessors: Seq[Accessor] = Nil,
  latestEvent: Option[SystemEvent],
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[VocabularyF]
  with Accessible
  with Holder[Concept] {

  override def toStringLang(implicit messages: Messages): String = model.name.getOrElse(id)
}