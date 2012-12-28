package models.forms

import play.api.data._
import play.api.data.Forms._
import defines.{EntityType, PublicationStatus}
import models.Annotations
import models.base.{AttributeSet, Persistable,TemporalEntity}


case object IsadG {
  /* ISAD(G)-based field set */
  val NAME = "name"
  val IDENTIFIER = "identifier"
  val TITLE = "title"
  val ADMIN_BIOG = "adminBiogHist"
  val ARCH_HIST = "archivalHistory"
  val ACQUISITION = "acquisition"
  val SCOPE_CONTENT = "scopeAndContent"
  val APPRAISAL = "appraisal"
  val ACCRUALS = "accruals"
  val SYS_ARR = "systemOfArrangement"
  val PUB_STATUS = "publicationStatus"
  val ACCESS_COND = "conditionsOfAccess"
  val REPROD_COND = "conditionsOfReproduction"
  val PHYSICAL_CHARS = "physicalCharacteristics"
  val FINDING_AIDS = "findingAids"
  val LOCATION_ORIGINALS = "locationOfOriginals"
  val LOCATION_COPIES = "locationOfCopies"
  val RELATED_UNITS = "relatedUnitsOfDescription"
  val PUBLICATION_NOTE = "publicationNote"

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
}

case class DocumentaryUnitDescriptionF(
  val id: Option[String],
  val languageCode: String,
  val title: Option[String] = None,
  val context: DocumentaryUnitDescriptionF.Context,
  val content: DocumentaryUnitDescriptionF.Content,
  val conditions: DocumentaryUnitDescriptionF.Conditions,
  val materials: DocumentaryUnitDescriptionF.Materials
) extends Persistable {
  val isA = EntityType.DocumentaryUnitDescription
}

object DocumentaryUnitDescriptionF {
  case class Context(
    val adminBiogHist: Option[String] = None,
    val archivalHistory: Option[String] = None,
    val acquisition: Option[String] = None) extends AttributeSet

  case class Content(
    val scopeAndContent: Option[String] = None,
    val appraisal: Option[String] = None,
    val accruals: Option[String] = None,
    val systemOfArrangement: Option[String] = None) extends AttributeSet

  case class Conditions(
    val conditionsOfAccess: Option[String] = None,
    val conditionsOfReproduction: Option[String] = None,
    val physicalCharacteristics: Option[String] = None,
    val findingAids: Option[String] = None) extends AttributeSet

  case class Materials(
    val locationOfOriginals: Option[String] = None,
    val locationOfCopies: Option[String] = None,
    val relatedUnitsOfDescription: Option[String] = None,
    val publicationNote: Option[String] = None) extends AttributeSet
}



object DocumentaryUnitForm {

  import DocumentaryUnitDescriptionF._
  import IsadG._

  val form = Form(
      mapping(
    		"id" -> optional(nonEmptyText),
    		"identifier" -> nonEmptyText,
    		NAME -> nonEmptyText,
    		PUB_STATUS -> optional(enum(defines.PublicationStatus)),
        "dates" -> list(DatePeriodForm.form.mapping),
    		"descriptions" -> list(
    		  mapping(
    		    "id" -> optional(nonEmptyText),
    		    "languageCode" -> nonEmptyText,
    		    TITLE -> optional(nonEmptyText),
    		    "context" -> mapping(
    		        ADMIN_BIOG -> optional(text),
    		        ARCH_HIST -> optional(text),
    		        ACQUISITION -> optional(text)
    		    )(Context.apply)(Context.unapply),
    		    "content" -> mapping(
    		        SCOPE_CONTENT -> optional(text),
    		        APPRAISAL -> optional(text),
    		        ACCRUALS -> optional(text),
    		        SYS_ARR -> optional(text)
    		    )(Content.apply)(Content.unapply),
    		    "conditions" -> mapping(
    		        ACCESS_COND -> optional(text),
    		        REPROD_COND -> optional(text),
    		        PHYSICAL_CHARS -> optional(text),
    		        FINDING_AIDS -> optional(text)
    		    )(Conditions.apply)(Conditions.unapply),
    		    "materials" -> mapping(
    		        LOCATION_ORIGINALS -> optional(text),
    		        LOCATION_COPIES -> optional(text),
    		        RELATED_UNITS -> optional(text),
    		        PUBLICATION_NOTE -> optional(text)
    		    )(Materials.apply)(Materials.unapply)
    		  )(DocumentaryUnitDescriptionF.apply)(DocumentaryUnitDescriptionF.unapply)
            )
      )(DocumentaryUnitF.apply)(DocumentaryUnitF.unapply)
  )
}
