package models

import defines._
import defines.EnumUtils._
import models.base._

import models.base.Persistable
import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import backend.rest.Constants


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

  val OTHER_IDENTIFIERS = "otherIdentifiers"
  val PUBLICATION_STATUS = "publicationStatus"
  final val SCOPE = "scope"
  final val COPYRIGHT = "copyright"

  implicit object Converter extends RestConvertable[DocumentaryUnitF] with ClientConvertable[DocumentaryUnitF] {
    val restFormat = models.json.DocumentaryUnitFormat.restFormat

    private implicit val docDescFmt = DocumentaryUnitDescriptionF.Converter.clientFormat
    val clientFormat = Json.format[DocumentaryUnitF]
  }
}

case class DocumentaryUnitF(
  isA: EntityType.Value = EntityType.DocumentaryUnit,
  id: Option[String] = None,
  identifier: String,
  otherIdentifiers: Option[List[String]] = None,
  publicationStatus: Option[PublicationStatus.Value] = None,
  copyrightStatus: Option[DocumentaryUnitF.CopyrightStatus.Value] = Some(DocumentaryUnitF.CopyrightStatus.Unknown),
  scope: Option[DocumentaryUnitF.Scope.Value] = Some(DocumentaryUnitF.Scope.Low),

  @Annotations.Relation(Ontology.DESCRIPTION_FOR_ENTITY)
  descriptions: List[DocumentaryUnitDescriptionF] = Nil
) extends Model
  with Persistable
  with Described[DocumentaryUnitDescriptionF] {
  def withDescription(d: DocumentaryUnitDescriptionF): DocumentaryUnitF = copy(descriptions = descriptions ++ List(d))

  /**
   * Get a description with a given id.
   * @param did
   * @return
   */
  override def description(did: String): Option[DocumentaryUnitDescriptionF] = descriptions.find(d => d.id.isDefined && d.id.get == did)

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

object DocumentaryUnit {
  implicit object Converter extends RestReadable[DocumentaryUnit] with ClientConvertable[DocumentaryUnit] {
    implicit val restReads = json.DocumentaryUnitFormat.metaReads

    val clientFormat: Format[DocumentaryUnit] = (
      __.format[DocumentaryUnitF](DocumentaryUnitF.Converter.clientFormat) and
        (__ \ "holder").formatNullable[Repository](Repository.Converter.clientFormat) and
        (__ \ "parent").lazyFormatNullable[DocumentaryUnit](clientFormat) and
        nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(DocumentaryUnit.apply _, unlift(DocumentaryUnit.unapply _))
  }

  implicit object Resource extends RestResource[DocumentaryUnit] {
    val entityType = EntityType.DocumentaryUnit

    /**
     * When displaying doc units we need the
     * repositories urlPattern to create an external link. However this
     * is not a mandatory property and thus not returned by the REST
     * interface by default, unless we specify it explicitly.
     */
    override def defaultParams = Seq(Constants.INCLUDE_PROPERTIES_PARAM -> RepositoryF.URL_PATTERN)
  }
}

case class DocumentaryUnit(
  model: DocumentaryUnitF,
  holder: Option[Repository] = None,
  parent: Option[DocumentaryUnit] = None,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[DocumentaryUnitF]
  with DescribedMeta[DocumentaryUnitDescriptionF, DocumentaryUnitF]
  with Hierarchical[DocumentaryUnit]
  with Holder[DocumentaryUnit]
  with Accessible {
}
