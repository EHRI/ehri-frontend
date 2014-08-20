package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{ContentTypes, EntityType, PublicationStatus}

import play.api.libs.json._
import models.base._
import models.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import java.net.URL
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject
import play.api.i18n.Lang
import backend.{BackendReadable, BackendContentType, BackendResource, BackendWriteable}


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
    (__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).nullableListReads[RepositoryDescriptionF] and
    (__ \ DATA \ PRIORITY).readNullable[Int] and
    (__ \ DATA \ URL_PATTERN).readNullable[String] and
    (__ \ DATA \ LOGO_URL).readNullable[String]
  )(RepositoryF.apply _)

  implicit val repositoryFormat: Format[RepositoryF] = Format(repositoryReads,repositoryWrites)

  implicit object Converter extends BackendWriteable[RepositoryF] {
    val restFormat = repositoryFormat
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
  with Aliased
  with DescribedMeta[RepositoryDescriptionF,RepositoryF]
  with Accessible
  with Holder[DocumentaryUnit] {

  override def allNames(implicit lang: Lang) = model.primaryDescription(lang) match {
    case Some(desc) => desc.name :: (desc.otherFormsOfName.toList.flatten ++ desc.parallelFormsOfName.toList.flatten)
    case None => Seq(toStringLang(lang))
  }

  override def toStringAbbr(implicit lang: Lang): String =
    allNames.reduceLeft( (a, b) => if (a.length < b.length) a else b)

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
    (__ \ RELATIONSHIPS \ REPOSITORY_HAS_COUNTRY).nullableHeadReads[Country] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyNullableListReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).nullableHeadReads[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Repository.apply _)

  implicit object Converter extends BackendReadable[Repository] {
    val restReads = metaReads
  }

  implicit object Resource extends BackendResource[Repository] with BackendContentType[Repository] {
    val entityType = EntityType.Repository
    val contentType = ContentTypes.Repository
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