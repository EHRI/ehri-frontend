package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus}

import play.api.libs.json._
import defines.EnumUtils._
import models.base._
import play.api.i18n.Lang


object RepositoryF {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"
  final val COUNTRY_REL = "hasCountry"

  val PUBLICATION_STATUS = "publicationStatus"
  final val PRIORITY = "priority"

  implicit val repositoryFormat = json.RepositoryFormat.repositoryFormat
}

case class RepositoryF(
  id: Option[String],
  identifier: String,
  publicationStatus: Option[PublicationStatus.Value] = None,
  @Annotations.Relation(RepositoryF.DESC_REL) descriptions: List[RepositoryDescriptionF] = Nil,
  priority: Option[Int] = None
) extends Persistable {
  val isA = EntityType.Repository

  def toJson: JsValue = Json.toJson(this)
}


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

  lazy val formable: RepositoryF = Json.toJson(e).as[RepositoryF]
  lazy val formableOpt: Option[RepositoryF] = Json.toJson(e).asOpt[RepositoryF]

  override def toStringAbbr(implicit lang: Lang) = {
    val otherNames: List[String] = descriptions.flatMap(_.listProperty(Isdiah.OTHER_FORMS_OF_NAME).getOrElse(Nil))
    val names: List[String] = (toStringLang :: otherNames)
    names.sortBy((s: String) => s.length).head
  }


  override def toString = {
    descriptions.headOption.flatMap(d => d.stringProperty(Isdiah.AUTHORIZED_FORM_OF_NAME)).getOrElse(id)
  }
}

