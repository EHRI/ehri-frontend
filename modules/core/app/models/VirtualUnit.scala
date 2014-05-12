package models

import defines._
import models.base._

import models.base.Persistable
import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import backend.rest.Constants
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject


object VirtualUnitF {

  import models.Entity._
  import Ontology._

  implicit val virtualUnitWrites: Writes[VirtualUnitF] = new Writes[VirtualUnitF] {
    def writes(d: VirtualUnitF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier
        ),
        RELATIONSHIPS -> Json.obj(
          Ontology.DESCRIPTION_FOR_ENTITY -> Json.toJson(d.descriptions.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val virtualUnitReads: Reads[VirtualUnitF] = (
    (__ \ TYPE).readIfEquals(EntityType.VirtualUnit) and
      (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).lazyReadNullable[List[DocumentaryUnitDescriptionF]](
        Reads.list[DocumentaryUnitDescriptionF]).map(_.getOrElse(List.empty[DocumentaryUnitDescriptionF]))
    )(VirtualUnitF.apply _)

  implicit val VirtualUnitFormat: Format[VirtualUnitF] = Format(virtualUnitReads,virtualUnitWrites)

  implicit object Converter extends RestConvertable[VirtualUnitF] with ClientConvertable[VirtualUnitF] {
    val restFormat = VirtualUnitFormat
    val clientFormat = Json.format[VirtualUnitF]
  }
}

case class VirtualUnitF(
  isA: EntityType.Value = EntityType.VirtualUnit,
  id: Option[String] = None,
  identifier: String,
  @Annotations.Relation(Ontology.DESCRIPTION_FOR_ENTITY)
  descriptions: List[DocumentaryUnitDescriptionF] = Nil
) extends Model
  with Persistable
  with Described[DocumentaryUnitDescriptionF] {

  override def description(did: String): Option[DocumentaryUnitDescriptionF]
      = descriptions.find(d => d.id.isDefined && d.id.get == did)
}

object VirtualUnit {
  import models.Entity._
  import models.VirtualUnitF._
  import Ontology._

  implicit val metaReads: Reads[VirtualUnit] = (
    __.read[VirtualUnitF](virtualUnitReads) and
    (__ \ RELATIONSHIPS \ VC_DESCRIBED_BY).nullableListReads(
      DocumentaryUnitDescriptionF.Converter.restFormat) and
    (__ \ RELATIONSHIPS \ VC_HAS_AUTHOR).nullableHeadReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ VC_IS_PART_OF).lazyNullableHeadReads(metaReads) and
    (__ \ RELATIONSHIPS \ DOC_IS_CHILD_OF).nullableHeadReads[Repository] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyNullableListReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyNullableHeadReads(
      SystemEvent.Converter.restReads) and
    (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
  )(VirtualUnit.apply _)


  implicit object Converter extends RestReadable[VirtualUnit] with ClientConvertable[VirtualUnit] {
    implicit val restReads = metaReads

    val clientFormat: Format[VirtualUnit] = (
      __.format[VirtualUnitF](VirtualUnitF.Converter.clientFormat) and
      (__ \ "descriptions").nullableListFormat(DocumentaryUnitDescriptionF.Converter.clientFormat) and
      (__ \ "author").formatNullable[Accessor](Accessor.Converter.clientFormat) and
      (__ \ "parent").lazyFormatNullable[VirtualUnit](clientFormat) and
      (__ \ "holder").formatNullable[Repository](Repository.Converter.clientFormat) and
      (__ \ "accessibleTo").nullableListFormat(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(VirtualUnit.apply _, unlift(VirtualUnit.unapply))
  }

  implicit object Resource extends RestResource[VirtualUnit] {
    val entityType = EntityType.VirtualUnit

    /**
     * When displaying doc units we need the
     * repositories urlPattern to create an external link. However this
     * is not a mandatory property and thus not returned by the REST
     * interface by default, unless we specify it explicitly.
     */
    override def defaultParams = Seq(
      Constants.INCLUDE_PROPERTIES_PARAM -> RepositoryF.URL_PATTERN,
      Constants.INCLUDE_PROPERTIES_PARAM -> Isdiah.OTHER_FORMS_OF_NAME,
      Constants.INCLUDE_PROPERTIES_PARAM -> Isdiah.PARALLEL_FORMS_OF_NAME,
      Constants.INCLUDE_PROPERTIES_PARAM -> RepositoryF.LOGO_URL
    )
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.VirtualUnit),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText,
      "descriptions" -> list(DocumentaryUnitDescription.form.mapping)
    )(VirtualUnitF.apply)(VirtualUnitF.unapply)
  )
}

case class VirtualUnit(
  model: VirtualUnitF,
  descriptionRefs: List[DocumentaryUnitDescriptionF] = List.empty,
  author: Option[Accessor] = None,
  parent: Option[VirtualUnit] = None,
  holder: Option[Repository] = None,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[VirtualUnitF]
  with Hierarchical[VirtualUnit]
  with Holder[VirtualUnit]
  with DescribedMeta[DocumentaryUnitDescriptionF, VirtualUnitF]
  with Accessible {

  def allDescriptions: List[DocumentaryUnitDescriptionF]
    = descriptionRefs ++ model.descriptions

  def asDocumentaryUnit: DocumentaryUnit = new DocumentaryUnit(
    new DocumentaryUnitF(
      id = model.id,
      identifier = model.identifier,
      descriptions = descriptionRefs ++ model.descriptions
    ),
    holder = holder,
    accessors = accessors
  )
}
