package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus}

import play.api.libs.json._
import defines.EnumUtils._
import models.base._
import models.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import java.net.URL
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject


object RepositoryF {

  val PUBLICATION_STATUS = "publicationStatus"
  final val PRIORITY = "priority"
  final val URL_PATTERN = "urlPattern"
  final val LOGO_URL = "logoUrl"

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

  priority: Option[Int] = None,
  urlPattern: Option[String] = None,
  logoUrl: Option[String] = None
) extends Model
  with Persistable
  with Described[RepositoryDescriptionF]

case class Repository(
                       model: RepositoryF,
                       country: Option[Country] = None,
                       accessors: List[Accessor] = Nil,
                       latestEvent: Option[SystemEvent] = None,
                       meta: JsObject = JsObject(Seq())
                       ) extends AnyModel
with MetaModel[RepositoryF]
with DescribedMeta[RepositoryDescriptionF,RepositoryF]
with Accessible
with Holder[DocumentaryUnit] {

  def url: Option[URL] = (for {
    desc <- descriptions
    address <- desc.addresses
    url <- address.url if utils.forms.isValidUrl(url)
  } yield url).headOption.map(new URL(_))
}

object Repository {
  implicit object Converter extends ClientConvertable[Repository] with RestReadable[Repository] {
    val restReads = models.json.RepositoryFormat.metaReads

    val clientFormat: Format[Repository] = (
      __.format[RepositoryF](RepositoryF.Converter.clientFormat) and
      (__ \ "country").formatNullable[Country](Country.Converter.clientFormat) and
      nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Repository.apply _, unlift(Repository.unapply _))
  }

  implicit object Resource extends RestResource[Repository] {
    val entityType = EntityType.Repository
  }

  import RepositoryF._

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
      Entity.ISA -> ignored(EntityType.Repository),
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=2), // TODO: Increase to > 2, not done yet 'cos of test fixtures
      PUBLICATION_STATUS -> optional(models.forms.enum(defines.PublicationStatus)),
      "descriptions" -> list(RepositoryDescription.form.mapping),
      PRIORITY -> optional(number(min = -1, max = 5)),
      URL_PATTERN -> optional(nonEmptyText verifying("errors.badUrlPattern", fields => fields match {
        case pattern => validateUrlPattern(pattern)
      })),
      LOGO_URL -> optional(nonEmptyText verifying("error.badUrl", fields => fields match {
        case url => utils.forms.isValidUrl(url)
      }))
    )(RepositoryF.apply)(RepositoryF.unapply)
  )

}