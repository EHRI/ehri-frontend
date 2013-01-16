package models.forms

import play.api.data._
import play.api.data.Forms._
import defines._
import models.{Entity, Annotations}
import models.base.{DescribedEntity, AttributeSet, Persistable, TemporalEntity}
import play.api.libs.json.{Json, JsString, JsValue}
import play.api.libs.json
import defines.EnumWriter.enumWrites


case object IsadG {
  /* ISAD(G)-based field set */
  val NAME = "name"
  val TITLE = "title"
  val PUB_STATUS = "publicationStatus"
  val LANG_CODE = "languageCode"

  val CONTEXT_AREA = "context"
  val ADMIN_BIOG = "adminBiogHist"
  val ARCH_HIST = "archivalHistory"
  val ACQUISITION = "acquisition"

  val CONTENT_AREA = "content"
  val SCOPE_CONTENT = "scopeAndContent"
  val APPRAISAL = "appraisal"
  val ACCRUALS = "accruals"
  val SYS_ARR = "systemOfArrangement"

  val CONDITIONS_AREA = "conditions"
  val ACCESS_COND = "conditionsOfAccess"
  val REPROD_COND = "conditionsOfReproduction"
  val PHYSICAL_CHARS = "physicalCharacteristics"
  val FINDING_AIDS = "findingAids"

  val MATERIALS_AREA = "materials"
  val LOCATION_ORIGINALS = "locationOfOriginals"
  val LOCATION_COPIES = "locationOfCopies"
  val RELATED_UNITS = "relatedUnitsOfDescription"
  val PUBLICATION_NOTE = "publicationNote"

  val CONTROL_AREA = "control"
  val ARCHIVIST_NOTE = "archivistNote"
  val RULES_CONVENTIONS = "rulesAndConventions"
  val DATES_DESCRIPTIONS = "datesOfDescriptions"
}


object DocumentaryUnitF {

  final val DESC_REL = "describes"
  final val ACCESS_REL = "access"
  final val HELD_REL = "heldBy"
  final val CHILD_REL = "childOf"

}

case class DocumentaryUnitF(
  val id: Option[String],
  val identifier: String,
  val name: String,
  val publicationStatus: Option[PublicationStatus.Value] = None,

  @Annotations.Relation(TemporalEntity.DATE_REL)
  val dates: List[DatePeriodF] = Nil,
  @Annotations.Relation(DocumentaryUnitF.DESC_REL)
  val descriptions: List[DocumentaryUnitDescriptionF] = Nil
) extends Persistable {
  val isA = EntityType.DocumentaryUnit

  def withDescription(d: DocumentaryUnitDescriptionF): DocumentaryUnitF = copy(descriptions = descriptions ++ List(d))

  def toJson: JsValue = {
    import Entity._
    import IsadG._
    import DocumentaryUnitF._

    Json.obj(
      ID -> id,
      TYPE -> isA,
      DATA -> Json.obj(
        IDENTIFIER -> identifier,
        NAME -> name,
        PUB_STATUS -> publicationStatus
      ),
      RELATIONSHIPS -> Json.obj(
        TemporalEntity.DATE_REL -> Json.toJson(dates.map(_.toJson).toSeq),
        DESC_REL -> Json.toJson(descriptions.map(_.toJson).toSeq)
      )
    )
  }
}

case class DocumentaryUnitDescriptionF(
  val id: Option[String],
  val languageCode: String,
  val title: Option[String] = None,
  val context: DocumentaryUnitDescriptionF.Context,
  val content: DocumentaryUnitDescriptionF.Content,
  val conditions: DocumentaryUnitDescriptionF.Conditions,
  val materials: DocumentaryUnitDescriptionF.Materials,
  val control: DocumentaryUnitDescriptionF.Control
) extends Persistable {
  val isA = EntityType.DocumentaryUnitDescription

  def toJson: JsValue = {
    import Entity._
    import IsadG._
    import DocumentaryUnitF._

    Json.obj(
      ID -> id,
      TYPE -> isA,
      DATA -> Json.obj(
        TITLE -> title,
        LANG_CODE -> languageCode,
        ADMIN_BIOG -> context.adminBiogHist,
        ARCH_HIST -> context.archivalHistory,
        ACQUISITION -> context.acquisition,
        SCOPE_CONTENT -> content.scopeAndContent,
        APPRAISAL -> content.appraisal,
        ACCRUALS -> content.accruals,
        SYS_ARR -> content.systemOfArrangement,
        ACCESS_COND -> conditions.conditionsOfAccess,
        REPROD_COND -> conditions.conditionsOfReproduction,
        PHYSICAL_CHARS -> conditions.physicalCharacteristics,
        FINDING_AIDS -> conditions.findingAids,
        LOCATION_ORIGINALS -> materials.locationOfOriginals,
        LOCATION_COPIES -> materials.locationOfCopies,
        RELATED_UNITS -> materials.relatedUnitsOfDescription,
        PUBLICATION_NOTE -> materials.publicationNote,
        ARCHIVIST_NOTE -> control.archivistNote,
        RULES_CONVENTIONS -> control.rulesAndConventions,
        DATES_DESCRIPTIONS -> control.datesOfDescriptions
      )
    )
  }
}

object DocumentaryUnitDescriptionF {

  case class Context(
    val adminBiogHist: Option[String] = None,
    val archivalHistory: Option[String] = None,
    val acquisition: Option[String] = None
  ) extends AttributeSet

  case class Content(
    val scopeAndContent: Option[String] = None,
    val appraisal: Option[String] = None,
    val accruals: Option[String] = None,
    val systemOfArrangement: Option[String] = None
  ) extends AttributeSet

  case class Conditions(
    val conditionsOfAccess: Option[String] = None,
    val conditionsOfReproduction: Option[String] = None,
    val physicalCharacteristics: Option[String] = None,
    val findingAids: Option[String] = None
  ) extends AttributeSet

  case class Materials(
    val locationOfOriginals: Option[String] = None,
    val locationOfCopies: Option[String] = None,
    val relatedUnitsOfDescription: Option[String] = None,
    val publicationNote: Option[String] = None
  ) extends AttributeSet

  case class Control(
    val archivistNote: Option[String] = None,
    val rulesAndConventions: Option[String] = None,
    val datesOfDescriptions: Option[String] = None
  )

}


object DocumentaryUnitForm {

  import DocumentaryUnitDescriptionF._
  import IsadG._

  val form = Form(
    mapping(
      "id" -> optional(nonEmptyText),
      "identifier" -> nonEmptyText,
      NAME -> nonEmptyText,
      "publicationStatus" -> optional(enum(defines.PublicationStatus)),
      "dates" -> list(DatePeriodForm.form.mapping),
      "descriptions" -> list(
        mapping(
          "id" -> optional(nonEmptyText),
          "languageCode" -> nonEmptyText,
          TITLE -> optional(nonEmptyText),
          CONTEXT_AREA -> mapping(
            ADMIN_BIOG -> optional(text),
            ARCH_HIST -> optional(text),
            ACQUISITION -> optional(text)
          )(Context.apply)(Context.unapply),
          CONTENT_AREA -> mapping(
            SCOPE_CONTENT -> optional(text),
            APPRAISAL -> optional(text),
            ACCRUALS -> optional(text),
            SYS_ARR -> optional(text)
          )(Content.apply)(Content.unapply),
          CONDITIONS_AREA -> mapping(
            ACCESS_COND -> optional(text),
            REPROD_COND -> optional(text),
            PHYSICAL_CHARS -> optional(text),
            FINDING_AIDS -> optional(text)
          )(Conditions.apply)(Conditions.unapply),
          MATERIALS_AREA -> mapping(
            LOCATION_ORIGINALS -> optional(text),
            LOCATION_COPIES -> optional(text),
            RELATED_UNITS -> optional(text),
            PUBLICATION_NOTE -> optional(text)
          )(Materials.apply)(Materials.unapply),
          CONTROL_AREA -> mapping(
            ARCHIVIST_NOTE -> optional(text),
            RULES_CONVENTIONS -> optional(text),
            DATES_DESCRIPTIONS -> optional(text)
          )(Control.apply)(Control.unapply)
        )(DocumentaryUnitDescriptionF.apply)(DocumentaryUnitDescriptionF.unapply)
      )
    )(DocumentaryUnitF.apply)(DocumentaryUnitF.unapply)
  )
}
