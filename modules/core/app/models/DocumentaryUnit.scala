package models

import defines._
import defines.EnumUtils._
import models.base._

import models.base.Persistable
import models.json._
import play.api.libs.json._
import scala.Some
import scala.Some
import play.api.libs.functional.syntax._
import scala.Some
import eu.ehri.project.definitions.Ontology
import solr.{SolrIndexer, SolrConstants}
import solr.SolrConstants._
import play.api.libs.json.JsString
import scala.Some
import play.api.libs.json.JsObject


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
        (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat)
      )(DocumentaryUnit.apply _, unlift(DocumentaryUnit.unapply _))
  }

  val toSolr: JsObject => Seq[JsObject] = { js =>
    import SolrConstants._
    val c = js.as[DocumentaryUnit](Converter.restReads)
    val descriptionData = (js \ Entity.RELATIONSHIPS \ Ontology.DESCRIPTION_FOR_ENTITY)
      .asOpt[List[JsObject]].getOrElse(List.empty[JsObject])

    c.descriptions.zipWithIndex.map { case (desc, i) =>
      val data = SolrIndexer.dynamicData((descriptionData(i) \ Entity.DATA).as[JsObject])
      data ++ Json.obj(
        ID -> Json.toJson(desc.id),
        TYPE -> JsString(c.isA.toString),
        NAME_EXACT -> JsString(desc.name),
        LANGUAGE_CODE -> JsString(desc.languageCode),
        "scope" -> Json.toJson(c.model.scope.map(_.toString)),
        "copyrightStatus" -> Json.toJson(c.model.copyrightStatus.map(_.toString)),
        "identifier" -> c.model.identifier,
        "parentId" -> Json.toJson(c.parent.map(_.id)),
        "depthOfDescription" -> JsNumber(c.ancestors.size),
        ITEM_ID -> JsString(c.id),
        "repositoryId" -> Json.toJson(c.holder.map(_.id)),
        "repositoryName" -> c.holder.map(_.toStringLang),
        HOLDER_ID -> Json.toJson(c.holder.map(_.id)),
        HOLDER_NAME -> c.holder.map(_.toStringLang),
        RESTRICTED_FIELD -> JsBoolean(if (c.accessors.isEmpty) false else true),
        ACCESSOR_FIELD -> (if (c.accessors.isEmpty) List(ACCESSOR_ALL_PLACEHOLDER) else c.accessors.map(_.id)),
        LAST_MODIFIED -> c.latestEvent.map(_.model.datetime)
      )
    }
  }
}

case class DocumentaryUnit(
  model: DocumentaryUnitF,
  holder: Option[Repository] = None,
  parent: Option[DocumentaryUnit] = None,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None
) extends AnyModel
  with MetaModel[DocumentaryUnitF]
  with DescribedMeta[DocumentaryUnitDescriptionF, DocumentaryUnitF]
  with Hierarchical[DocumentaryUnit]
  with Accessible
