package models

import defines._
import defines.EnumUtils._
import models.base._

import models.base.{DescribedEntity, AttributeSet, Persistable, TemporalEntity}
import play.api.libs.json.{JsObject, Json, JsString, JsValue}
import models.json.{RestReadable, DocumentaryUnitFormat, ClientConvertable, RestConvertable}


object DocumentaryUnitF {

  object CopyrightStatus extends Enumeration {
    val Yes = Value("yes")
    val No = Value("no")
    val Unknown = Value("unknown")

    implicit val format = defines.EnumUtils.enumFormat(this)
  }

  object Scope extends Enumeration {
    val High = Value("high")
    val Medium = Value("medium")
    val Low = Value("low")

    implicit val format = defines.EnumUtils.enumFormat(this)
  }

  val PUBLICATION_STATUS = "publicationStatus"
  final val SCOPE = "scope"
  final val COPYRIGHT = "copyright"

  final val DESC_REL = "describes"
  final val ACCESS_REL = "access"
  final val HELD_REL = "heldBy"
  final val CHILD_REL = "childOf"

  //lazy implicit val restFormat = json.DocumentaryUnitFormat.restFormat

  implicit object Converter extends RestConvertable[DocumentaryUnitF] with ClientConvertable[DocumentaryUnitF] {
    lazy val restFormat = models.json.rest.documentaryUnitFormat
    lazy val clientFormat = models.json.client.documentaryUnitFormat
  }
}

case class DocumentaryUnitF(
  isA: EntityType.Value = EntityType.DocumentaryUnit,
  id: Option[String] = None,
  identifier: String,
  publicationStatus: Option[PublicationStatus.Value] = None,
  copyrightStatus: Option[DocumentaryUnitF.CopyrightStatus.Value] = Some(DocumentaryUnitF.CopyrightStatus.Unknown),
  scope: Option[DocumentaryUnitF.Scope.Value] = Some(DocumentaryUnitF.Scope.Low),

  @Annotations.Relation(DocumentaryUnitF.DESC_REL)
  descriptions: List[DocumentaryUnitDescriptionF] = Nil
) extends Persistable {
  def withDescription(d: DocumentaryUnitDescriptionF): DocumentaryUnitF = copy(descriptions = descriptions ++ List(d))

  /**
   * Get a description with a given id.
   * @param did
   * @return
   */
  def description(did: String): Option[DocumentaryUnitDescriptionF] = descriptions.find(d => d.id.isDefined && d.id.get == did)

  /**
   * Replace an existing description with the same id as this one, or add
   * this one to the end of the list of descriptions.
   * @param d
   * @return
   */
  def replaceDescription(d: DocumentaryUnitDescriptionF): DocumentaryUnitF = d.id.map {
    did =>
    // If the description has an id, replace the existing one with that id
      val newDescriptions = descriptions.map {
        dm =>
          if (dm.id.isDefined && dm.id.get == did) d else dm
      }
      copy(descriptions = newDescriptions)
  } getOrElse {
    withDescription(d)
  }
}


case class DocumentaryUnit(val e: Entity) extends NamedEntity
  with AccessibleEntity
  with AnnotatableEntity
  with LinkableEntity
  with HierarchicalEntity[DocumentaryUnit]
  with DescribedEntity[DocumentaryUnitDescription]
  with Formable[DocumentaryUnitF] {

  import DocumentaryUnitF._
  import DescribedEntity._

  val hierarchyRelationName = CHILD_REL

  val holder: Option[Repository] = e.relations(HELD_REL).headOption.map(Repository(_))
  val parent: Option[DocumentaryUnit] = e.relations(CHILD_REL).headOption.map(DocumentaryUnit(_))
  val publicationStatus = e.property(IsadG.PUB_STATUS).flatMap(enumReads(PublicationStatus).reads(_).asOpt)
  // NB: There is a default value of copyright status, so use 'unknown'.
  val copyrightStatus = e.property(COPYRIGHT).flatMap(enumReads(CopyrightStatus).reads(_).asOpt)
    .orElse(Some(CopyrightStatus.Unknown))
  val scope = e.property(SCOPE).flatMap(enumReads(Scope).reads(_).asOpt)

  def descriptions: List[DocumentaryUnitDescription] = e.relations(DESCRIBES_REL)
    .map(DocumentaryUnitDescription(_)).sortBy(d => d.languageCode)


  import json.DocumentaryUnitFormat._
  lazy val formable: DocumentaryUnitF = Json.toJson(e).as[DocumentaryUnitF](json.DocumentaryUnitFormat.restFormat)
  lazy val formableOpt: Option[DocumentaryUnitF] = Json.toJson(e).asOpt[DocumentaryUnitF](json.DocumentaryUnitFormat.restFormat)
}

object DocumentaryUnitMeta {
  implicit object Converter extends ClientConvertable[DocumentaryUnitMeta] with RestReadable[DocumentaryUnitMeta] {
    val restReads = models.json.DocumentaryUnitFormat.metaReads
    val clientFormat = models.json.client.documentaryUnitMetaFormat
  }
}

case class DocumentaryUnitMeta(
  json: JsObject,
  model: DocumentaryUnitF,
  holder: Option[RepositoryMeta] = None,
  parent: Option[DocumentaryUnitMeta] = None,
  latestEvent: Option[SystemEventMeta] = None
) extends MetaModel