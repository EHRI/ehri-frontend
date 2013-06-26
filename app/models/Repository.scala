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


case class Repository(val e: Entity)
  extends AccessibleEntity
  with AnnotatableEntity
  with LinkableEntity
  with DescribedEntity[RepositoryDescription]
  with Formable[RepositoryF] {
  override def descriptions: List[RepositoryDescription] = e.relations(DescribedEntity.DESCRIBES_REL).map(RepositoryDescription(_))

  // Shortcuts...
  val publicationStatus = e.property(RepositoryF.PUBLICATION_STATUS).flatMap(enumReads(PublicationStatus).reads(_).asOpt)
  val priority = e.property(RepositoryF.PRIORITY).flatMap(_.asOpt[Int])

  val country: Option[Country] = e.relations(RepositoryF.COUNTRY_REL).headOption.map(Country(_))

  lazy val formable: RepositoryF = Json.toJson(e).as[RepositoryF](json.RepositoryFormat.restFormat)
  lazy val formableOpt: Option[RepositoryF] = Json.toJson(e).asOpt[RepositoryF](json.RepositoryFormat.restFormat)

  override def toStringAbbr(implicit lang: Lang) = {
    val otherNames: List[String] = descriptions.flatMap(_.listProperty(Isdiah.OTHER_FORMS_OF_NAME).getOrElse(Nil))
    val names: List[String] = (toStringLang :: otherNames)
    names.sortBy((s: String) => s.length).head
  }


  override def toString = {
    descriptions.headOption.flatMap(d => d.stringProperty(Isdiah.AUTHORIZED_FORM_OF_NAME)).getOrElse(id)
  }
}

object RepositoryMeta {
  implicit object Converter extends ClientConvertable[RepositoryMeta] with RestReadable[RepositoryMeta] {
    val restReads = models.json.RepositoryFormat.metaReads
    val clientFormat = models.json.client.repositoryMetaFormat
  }
}

case class RepositoryMeta(
  model: RepositoryF,
  country: Option[CountryMeta] = None,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEventMeta] = None
) extends MetaModel[RepositoryF]
  with Accessible