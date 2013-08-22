package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus}

import play.api.libs.json._
import defines.EnumUtils._
import models.base._
import play.api.i18n.Lang
import models.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import solr.{SolrIndexer, SolrConstants}
import solr.SolrConstants._
import play.api.libs.json.JsString
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject


object RepositoryF {

  val PUBLICATION_STATUS = "publicationStatus"
  final val PRIORITY = "priority"

  implicit object Converter extends RestConvertable[RepositoryF] with ClientConvertable[RepositoryF] {
    val restFormat = models.json.RepositoryFormat.restFormat

    private implicit val repoDescFmt = RepositoryDescriptionF.Converter.clientFormat
    val clientFormat = Json.format[RepositoryF]
  }
}

case class RepositoryF(
  isA: EntityType.Value = EntityType.Repository,
  id: Option[String],
  identifier: String,
  publicationStatus: Option[PublicationStatus.Value] = None,

  @Annotations.Relation(Ontology.DESCRIPTION_FOR_ENTITY)
  descriptions: List[RepositoryDescriptionF] = Nil,

  priority: Option[Int] = None
) extends Model
  with Persistable
  with Described[RepositoryDescriptionF]


object Repository {
  implicit object Converter extends ClientConvertable[Repository] with RestReadable[Repository] {
    val restReads = models.json.RepositoryFormat.metaReads

    val clientFormat: Format[Repository] = (
      __.format[RepositoryF](RepositoryF.Converter.clientFormat) and
      (__ \ "country").formatNullable[Country](Country.Converter.clientFormat) and
      nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat)
    )(Repository.apply _, unlift(Repository.unapply _))
  }

  val toSolr: JsObject => Seq[JsObject] = { js =>
    import SolrConstants._
    val c = js.as[Repository](Converter.restReads)
    val descriptionData = (js \ Entity.RELATIONSHIPS \ Ontology.DESCRIPTION_FOR_ENTITY)
      .asOpt[List[JsObject]].getOrElse(List.empty[JsObject])

    c.descriptions.zipWithIndex.map { case (desc, i) =>
      val data = SolrIndexer.dynamicData((descriptionData(i) \ Entity.DATA).as[JsObject])
      data ++ Json.obj(
        ID -> Json.toJson(desc.id),
        TYPE -> JsString(c.isA.toString),
        NAME_EXACT -> JsString(desc.name),
        LANGUAGE_CODE -> JsString(desc.languageCode),
        ITEM_ID -> JsString(c.id),
        RESTRICTED_FIELD -> JsBoolean(if (c.accessors.isEmpty) false else true),
        ACCESSOR_FIELD -> (if (c.accessors.isEmpty) List(ACCESSOR_ALL_PLACEHOLDER) else c.accessors.map(_.id)),
        LAST_MODIFIED -> c.latestEvent.map(_.model.datetime),
        OTHER_NAMES -> Json.toJson(desc.otherFormsOfName.toList.flatten),
        PARALLEL_NAMES -> Json.toJson(desc.parallelFormsOfName.toList.flatten),
        "identifier" -> c.model.identifier,
        "countryCode" -> Json.toJson(c.country.map(_.id)),
        "countryName" -> Json.toJson(c.country.map(c => views.Helpers.countryCodeToName(c.id)(Lang.defaultLang))),
        "addresses" -> Json.toJson(desc.addresses.map(_.toString)),
        "priority" -> Json.toJson(c.model.priority)
      )
    }
  }
}

case class Repository(
  model: RepositoryF,
  country: Option[Country] = None,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None
) extends AnyModel
  with MetaModel[RepositoryF]
  with DescribedMeta[RepositoryDescriptionF,RepositoryF]
  with Accessible