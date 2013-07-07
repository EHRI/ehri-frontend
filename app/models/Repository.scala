package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus}

import play.api.libs.json._
import defines.EnumUtils._
import models.base._
import play.api.i18n.Lang
import models.json.{RestReadable, ClientConvertable, RestConvertable}


object RepositoryF {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"
  final val COUNTRY_REL = "hasCountry"

  val PUBLICATION_STATUS = "publicationStatus"
  final val PRIORITY = "priority"

  implicit object Converter extends RestConvertable[RepositoryF] with ClientConvertable[RepositoryF] {
    lazy val restFormat = models.json.rest.repositoryFormat
    lazy val clientFormat = models.json.client.repositoryFormat
  }
}

case class RepositoryF(
  isA: EntityType.Value = EntityType.Repository,
  id: Option[String],
  identifier: String,
  publicationStatus: Option[PublicationStatus.Value] = None,
  @Annotations.Relation(RepositoryF.DESC_REL) descriptions: List[RepositoryDescriptionF] = Nil,
  priority: Option[Int] = None
) extends Model with Persistable with Described[RepositoryDescriptionF]


object RepositoryMeta {
  implicit object Converter extends ClientConvertable[RepositoryMeta] with RestReadable[RepositoryMeta] {
    val restReads = models.json.RepositoryFormat.metaReads
    val clientFormat = models.json.client.repositoryMetaFormat

    AnyModel.registerRest(EntityType.Repository, restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerClient(EntityType.Repository, clientFormat.asInstanceOf[Format[AnyModel]])
  }
}

case class RepositoryMeta(
  model: RepositoryF,
  country: Option[CountryMeta] = None,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEventMeta] = None
) extends AnyModel
  with MetaModel[RepositoryF]
  with DescribedMeta[RepositoryDescriptionF,RepositoryF]
  with Accessible