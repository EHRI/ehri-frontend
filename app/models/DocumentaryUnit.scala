package models

import defines._
import models.base._

import models.base.{DescribedEntity, AttributeSet, Persistable, TemporalEntity}
import play.api.libs.json.{Json, JsString, JsValue}
import defines.EnumWriter.enumWrites
import defines.enum



case class DocumentaryUnit(val e: Entity) extends NamedEntity
  with AccessibleEntity
  with AnnotatableEntity
  with HierarchicalEntity[DocumentaryUnit]
  with DescribedEntity
  with Formable[DocumentaryUnitF] {

  import DocumentaryUnitF._
  import DescribedEntity._

  val hierarchyRelationName = CHILD_REL

  val holder: Option[Repository] = e.relations(HELD_REL).headOption.map(Repository(_))
  val parent: Option[DocumentaryUnit] = e.relations(CHILD_REL).headOption.map(DocumentaryUnit(_))
  val publicationStatus = e.property(IsadG.PUB_STATUS).flatMap(enum(PublicationStatus).reads(_).asOpt)
  // NB: There is a default value of copyright status, so use 'unknown'.
  val copyrightStatus = e.property(COPYRIGHT).flatMap(enum(CopyrightStatus).reads(_).asOpt)
    .orElse(Some(CopyrightStatus.Unknown))
  val scope = e.property(SCOPE).flatMap(enum(Scope).reads(_).asOpt)

  override def descriptions: List[DocumentaryUnitDescription] = e.relations(DESCRIBES_REL).map(DocumentaryUnitDescription(_))

  import json.DocumentaryUnitFormat._
  lazy val formable: DocumentaryUnitF = Json.toJson(e).as[DocumentaryUnitF]
  lazy val formableOpt: Option[DocumentaryUnitF] = Json.toJson(e).asOpt[DocumentaryUnitF]
}


object DocumentaryUnitF {

  object CopyrightStatus extends Enumeration {
    val Yes = Value("yes")
    val No = Value("no")
    val Unknown = Value("unknown")
  }

  object Scope extends Enumeration {
    val High = Value("high")
    val Medium = Value("medium")
    val Low = Value("low")
  }

  val NAME = "name"
  val PUBLICATION_STATUS = "publicationStatus"
  final val SCOPE = "scope"
  final val COPYRIGHT = "copyright"

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
  val copyrightStatus: Option[DocumentaryUnitF.CopyrightStatus.Value] = Some(DocumentaryUnitF.CopyrightStatus.Unknown),
  val scope: Option[DocumentaryUnitF.Scope.Value] = Some(DocumentaryUnitF.Scope.Low),

  @Annotations.Relation(DocumentaryUnitF.DESC_REL)
  val descriptions: List[DocumentaryUnitDescriptionF] = Nil
) extends Persistable {
  val isA = EntityType.DocumentaryUnit

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

  import json.DocumentaryUnitFormat._
  def toJson: JsValue = Json.toJson(this)
}
