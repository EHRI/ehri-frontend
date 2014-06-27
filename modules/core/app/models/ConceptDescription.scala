package models

import defines.EntityType
import models.base._
import models.json._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.data.Form
import play.api.data.format.Formats._
import play.api.data.Forms._
import models.forms._

object ConceptDescriptionF {

  import eu.ehri.project.definitions.Ontology
  import Entity._
  import ConceptF._

  implicit val conceptDescriptionWrites = new Writes[ConceptDescriptionF] {
    def writes(d: ConceptDescriptionF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          LANG_CODE -> d.languageCode,
          PREFLABEL -> d.name,
          ALTLABEL -> d.altLabels,
          DEFINITION -> d.definition,
          SCOPENOTE -> d.scopeNote,
          LONGITUDE -> d.longitude,
          LATITUDE -> d.latitude
        ),
        RELATIONSHIPS -> Json.obj(
          Ontology.HAS_ACCESS_POINT -> Json.toJson(d.accessPoints.map(Json.toJson(_)).toSeq),
          Ontology.HAS_UNKNOWN_PROPERTY -> Json.toJson(d.unknownProperties.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val conceptDescriptionReads: Reads[ConceptDescriptionF] = (
    (__ \ TYPE).readIfEquals(EntityType.ConceptDescription) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ LANG_CODE).read[String] and
    (__ \ DATA \ PREFLABEL).read[String] and
    (__ \ DATA \ ALTLABEL).readListOrSingleNullable[String] and
    (__ \ DATA \ DEFINITION).readListOrSingleNullable[String] and
    (__ \ DATA \ SCOPENOTE).readListOrSingleNullable[String] and
    (__ \ DATA \ LONGITUDE).readNullable[BigDecimal] and
    (__ \ DATA \ LATITUDE).readNullable[BigDecimal] and
    (__ \ RELATIONSHIPS \ Ontology.HAS_ACCESS_POINT).nullableListReads[AccessPointF] and
    (__ \ RELATIONSHIPS \ Ontology.HAS_UNKNOWN_PROPERTY).nullableListReads[Entity]
  )(ConceptDescriptionF.apply _)

  implicit val conceptDescriptionFormat: Format[ConceptDescriptionF] = Format(conceptDescriptionReads,conceptDescriptionWrites)

  implicit object Converter extends RestConvertable[ConceptDescriptionF] with ClientConvertable[ConceptDescriptionF] {
    lazy val restFormat = conceptDescriptionFormat

    private implicit val accessPointFormat = AccessPointF.Converter.clientFormat
    lazy val clientFormat = Json.format[ConceptDescriptionF]
  }
}

case class ConceptDescriptionF(
  isA: EntityType.Value = EntityType.ConceptDescription,
  id: Option[String],
  languageCode: String,
  name: String,
  altLabels: Option[List[String]] = None,
  definition: Option[List[String]] = None,
  scopeNote: Option[List[String]] = None,
  longitude: Option[BigDecimal] = None,
  latitude: Option[BigDecimal] = None,
  accessPoints: List[AccessPointF] = Nil,
  unknownProperties: List[Entity] = Nil
) extends Model with Persistable with Description {

  def displayText = scopeNote.flatMap(_.headOption) orElse definition.flatMap(_.headOption)

  // NA - no single valued optional text fields
  // here...
  def toSeq = Seq()
}

object ConceptDescription {

  import ConceptF._
  import Entity._

  val form = Form(mapping(
    ISA -> ignored(EntityType.ConceptDescription),
    ID -> optional(nonEmptyText),
    LANG_CODE -> nonEmptyText,
    PREFLABEL -> nonEmptyText,
    ALTLABEL -> optional(list(nonEmptyText)),
    DEFINITION -> optional(list(nonEmptyText)),
    SCOPENOTE -> optional(list(nonEmptyText)),
    LONGITUDE -> optional(bigDecimal),
    LATITUDE -> optional(bigDecimal),
    ACCESS_POINTS -> list(AccessPoint.form.mapping),
    UNKNOWN_DATA -> list(entity)
  )(ConceptDescriptionF.apply)(ConceptDescriptionF.unapply))
}