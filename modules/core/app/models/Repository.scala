package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus}

import play.api.libs.json._
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

  import Entity._
  import Ontology._

  implicit val repositoryWrites: Writes[RepositoryF] = new Writes[RepositoryF] {
    def writes(d: RepositoryF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          PUBLICATION_STATUS -> d.publicationStatus,
          PRIORITY -> d.priority,
          URL_PATTERN -> d.urlPattern,
          LOGO_URL -> d.logoUrl
        ),
        RELATIONSHIPS -> Json.obj(
          DESCRIPTION_FOR_ENTITY -> Json.toJson(d.descriptions.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val repositoryReads: Reads[RepositoryF] = (
    (__ \ TYPE).readIfEquals(EntityType.Repository) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ IDENTIFIER).read[String] and
    (__ \ DATA \ PUBLICATION_STATUS).readNullable[PublicationStatus.Value] and
    // FIXME: This throws an error if an item has no descriptions - we should somehow
    // make it so that the path being missing is permissable but a validation error
    // is not.
    (__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).lazyReadNullable[List[RepositoryDescriptionF]](
      Reads.list[RepositoryDescriptionF]).map(_.getOrElse(List.empty[RepositoryDescriptionF])) and
    (__ \ DATA \ PRIORITY).readNullable[Int] and
    (__ \ DATA \ URL_PATTERN).readNullable[String] and
    (__ \ DATA \ LOGO_URL).readNullable[String]
  )(RepositoryF.apply _)

  implicit val repositoryFormat: Format[RepositoryF] = Format(repositoryReads,repositoryWrites)

  implicit object Converter extends RestConvertable[RepositoryF] with ClientConvertable[RepositoryF] {
    val restFormat = repositoryFormat

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
  import Entity._
  import RepositoryF._
  import Ontology._

  implicit lazy val metaReads: Reads[Repository] = (
    __.read[RepositoryF](repositoryReads) and
      // Country
      (__ \ RELATIONSHIPS \ REPOSITORY_HAS_COUNTRY).lazyReadNullable[List[Country]](
        Reads.list(Country.Converter.restReads)).map(_.flatMap(_.headOption)) and
      (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadNullable[List[Accessor]](
        Reads.list(Accessor.Converter.restReads)).map(_.toList.flatten) and
      (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyReadNullable[List[SystemEvent]](
        Reads.list(SystemEvent.Converter.restReads)).map(_.flatMap(_.headOption)) and
      (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
    )(Repository.apply _)

  implicit object Converter extends ClientConvertable[Repository] with RestReadable[Repository] {
    val restReads = metaReads

    val clientFormat: Format[Repository] = (
      __.format[RepositoryF](RepositoryF.Converter.clientFormat) and
      (__ \ "country").formatNullable[Country](Country.Converter.clientFormat) and
      (__ \ "accessibleTo").nullableListFormat(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Repository.apply _, unlift(Repository.unapply _))
  }

  implicit object Resource extends RestResource[Repository] {
    val entityType = EntityType.Repository
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