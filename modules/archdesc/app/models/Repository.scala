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