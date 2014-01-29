package models

import defines.EntityType
import models.base._
import models.json.{ClientConvertable, RestConvertable}
import play.api.libs.json.{Json, JsObject, JsValue}

object ConceptDescriptionF {

  implicit object Converter extends RestConvertable[ConceptDescriptionF] with ClientConvertable[ConceptDescriptionF] {
    lazy val restFormat = models.json.ConceptDescriptionFormat.restFormat

    private implicit val accessPointFormat = AccessPointF.Converter.clientFormat
    private implicit val entityFormat = json.entityFormat
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
  accessPoints: List[AccessPointF] = Nil,
  unknownProperties: List[Entity] = Nil
) extends Model with Persistable with Description {

  // NA - no single valued optional text fields
  // here...
  def toSeq = Seq()
}
