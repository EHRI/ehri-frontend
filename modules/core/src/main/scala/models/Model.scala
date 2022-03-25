package models

import org.apache.commons.lang3.StringUtils
import play.api.i18n.Messages
import play.api.libs.json.{KeyPathNode, _}
import utils.EnumUtils

import java.util.NoSuchElementException


trait Model extends WithId {
  type T <: ModelData

  def id: String = data.id.getOrElse(sys.error("Model data without ID!"))
  def isA: EntityType.Value = data.isA

  def data: T
  def meta: JsObject

  def contentType: Option[ContentTypes.Value] = try {
    Some(ContentTypes.withName(isA.toString))
  } catch {
    case _: NoSuchElementException => None
  }

  /**
    * Language-dependent version of the name. This is a fallback value.
    */
  def toStringLang(implicit messages: Messages): String = s"$isA: $id"

  /**
   * Abbreviated version of the canonical name
   */
  def toStringAbbr(implicit messages: Messages): String =
    StringUtils.abbreviate(toStringLang(messages), 80)
}

object Model {

  val readMap: PartialFunction[EntityType.Value, Reads[Model]] = {
    case EntityType.Repository => Repository.RepositoryResource.restReads.widen[Model]
    case EntityType.Country => Country.CountryResource.restReads.widen[Model]
    case EntityType.DocumentaryUnit => DocumentaryUnit.DocumentaryUnitResource.restReads.widen[Model]
    case EntityType.Vocabulary => Vocabulary.VocabularyResource.restReads.widen[Model]
    case EntityType.Concept => Concept.ConceptResource.restReads.widen[Model]
    case EntityType.HistoricalAgent => HistoricalAgent.HistoricalAgentResource.restReads.widen[Model]
    case EntityType.AuthoritativeSet => AuthoritativeSet.AuthoritativeSetResource.restReads.widen[Model]
    case EntityType.SystemEvent => SystemEvent.SystemEventResource.restReads.widen[Model]
    case EntityType.Group => Group.GroupResource.restReads.widen[Model]
    case EntityType.UserProfile => UserProfile.UserProfileResource.restReads.widen[Model]
    case EntityType.Link => Link.LinkResource.restReads.widen[Model]
    case EntityType.Annotation => Annotation.AnnotationResource.restReads.widen[Model]
    case EntityType.PermissionGrant => PermissionGrant.PermissionGrantResource.restReads.widen[Model]
    case EntityType.ContentType => DataContentType.Converter.restReads.widen[Model]
    case EntityType.AccessPoint => AccessPoint.Converter.restReads.widen[Model]
    case EntityType.VirtualUnit => VirtualUnit.VirtualUnitResource.restReads.widen[Model]
  }

  implicit object Converter extends Readable[Model] {
    implicit val restReads: Reads[Model] = Reads[Model] { json =>
      // Sniff the type...
      val et = (json \ Entity.TYPE).as(EnumUtils.enumReads(EntityType))
      readMap.lift(et).map { reads =>
        json.validate(reads)
      }.getOrElse {
        JsError(
          JsPath(List(KeyPathNode(Entity.TYPE))),
          JsonValidationError(s"Unregistered Model type: $et"))
      }
    }
  }

  /**
    * This function allows getting a dynamic Resource for an Accessor given
    * the entity type.
    */
  def resourceFor(t: EntityType.Value): Resource[Model] = new Resource[Model] {
    def entityType: EntityType.Value = t
    val restReads: Reads[Model] = Converter.restReads
  }
}
