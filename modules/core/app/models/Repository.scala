package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{ContentTypes, EntityType}
import play.api.libs.json._
import models.base._
import models.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import java.net.URL

import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.JsObject
import services.data.{ContentType, Writable}


object RepositoryF {

  val PUBLICATION_STATUS = "publicationStatus"
  final val PRIORITY = "priority"
  final val URL_PATTERN = "urlPattern"
  final val LOGO_URL = "logoUrl"

  import Entity._
  import Ontology._

  implicit val repositoryFormat: Format[RepositoryF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Repository) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ DATA \ PUBLICATION_STATUS).formatNullable[PublicationStatus.Value] and
    (__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).formatSeqOrEmpty[RepositoryDescriptionF] and
    (__ \ DATA \ PRIORITY).formatNullable[Int] and
    (__ \ DATA \ URL_PATTERN).formatNullable[String] and
    (__ \ DATA \ LOGO_URL).formatNullable[String]
  )(RepositoryF.apply, unlift(RepositoryF.unapply))

  implicit object Converter extends Writable[RepositoryF] {
    val restFormat: Format[RepositoryF] = repositoryFormat
  }
}



case class RepositoryF(
  isA: EntityType.Value = EntityType.Repository,
  id: Option[String],
  identifier: String,
  publicationStatus: Option[PublicationStatus.Value] = None,

  @models.relation(Ontology.DESCRIPTION_FOR_ENTITY)
  descriptions: Seq[RepositoryDescriptionF] = Nil,

  priority: Option[Int] = None,
  urlPattern: Option[String] = None,
  logoUrl: Option[String] = None
) extends Model
  with Persistable
  with Described[RepositoryDescriptionF]

case class Repository(
  model: RepositoryF,
  country: Option[Country] = None,
  accessors: Seq[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[RepositoryF]
  with Aliased
  with DescribedMeta[RepositoryDescriptionF,RepositoryF]
  with Accessible
  with Holder[DocumentaryUnit] {

  override def allNames(implicit messages: Messages): Seq[String] = model.primaryDescription(messages) match {
    case Some(desc) => desc.name +: (desc.otherFormsOfName.toSeq.flatten ++ desc.parallelFormsOfName.toSeq.flatten)
    case None => Seq(toStringLang(messages))
  }

  override def toStringAbbr(implicit messages: Messages): String =
    allNames.reduceLeft( (a, b) => if (a.length < b.length) a else b)

  def url: Option[URL] = (for {
    desc <- descriptions
    address <- desc.addresses
    url <- address.url if utils.forms.isValidUrl(url)
  } yield url).headOption.map(new URL(_))

  def emails: Seq[String] = for {
    desc <- descriptions
    address <- desc.addresses
    email <- address.email // TODO: Validate email?
  } yield email
}

object Repository {
  import Entity._
  import DescribedMeta._
  import RepositoryF._
  import Ontology._
  import utils.EnumUtils.enumMapping

  implicit lazy val metaReads: Reads[Repository] = (
    __.read[RepositoryF](repositoryFormat) and
    (__ \ RELATIONSHIPS \ REPOSITORY_HAS_COUNTRY).readHeadNullable[Country] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadSeqOrEmpty(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Repository.apply _)

  implicit object RepositoryResource extends ContentType[Repository]  {
    val entityType = EntityType.Repository
    val contentType = ContentTypes.Repository
    val restReads: Reads[Repository] = metaReads
  }

  /**
   * Validate an URL substitution pattern. Currently there's
   * only one valid pattern, into which the substition must
   * be {identifier}, i.e. http://collections.ushmm.org/search/irn{identifier}.
   */
  private def validateUrlPattern(s: String) = {
    val replace = "identifier"
    s.contains(s"{$replace}") && utils.forms
      .isValidUrl(s.replaceAll("\\{" + replace + "\\}", "test"))
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.Repository),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText(minLength=2), // TODO: Increase to > 2, not done yet 'cos of test fixtures
      PUBLICATION_STATUS -> optional(enumMapping(models.PublicationStatus)),
      DESCRIPTIONS -> seq(RepositoryDescription.form.mapping),
      PRIORITY -> optional(number(min = -1, max = 5)),
      URL_PATTERN -> optional(nonEmptyText verifying("errors.badUrlPattern",
        pattern => validateUrlPattern(pattern)
      )),
      LOGO_URL -> optional(nonEmptyText verifying("error.badUrl",
        url => utils.forms.isValidUrl(url)
      ))
    )(RepositoryF.apply)(RepositoryF.unapply)
  )

}