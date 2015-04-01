package models

import base._

import models.base.Persistable
import defines.{ContentTypes, EntityType}
import play.api.libs.json._
import models.json._
import play.api.i18n.Lang
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

  implicit val vocabularyWrites: Writes[VocabularyF] = new Writes[VocabularyF] {
    def writes(d: VocabularyF): JsValue = {
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

  implicit val vocabularyReads: Reads[VocabularyF] = (
    (__ \ TYPE).readIfEquals(EntityType.Vocabulary) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ IDENTIFIER).read[String] and
    (__ \ DATA \ NAME).readNullable[String] and
    (__ \ DATA \ DESCRIPTION).readNullable[String]
  )(VocabularyF.apply _)

  implicit val vocabularyFormat: Format[VocabularyF] = Format(vocabularyReads,vocabularyWrites)

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
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyNullableSeqReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).nullableHeadReads[SystemEvent] and
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

  override def toStringLang(implicit lang: Lang): String = model.name.getOrElse(id)
}