package models

import defines.EntityType
import models.base._
import models.json.{ClientConvertable, RestConvertable}

object ConceptDescriptionF {

  implicit object Converter extends RestConvertable[ConceptDescriptionF] with ClientConvertable[ConceptDescriptionF] {
    lazy val restFormat = models.json.rest.conceptDescriptionFormat
    lazy val clientFormat = models.json.client.conceptDescriptionFormat
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
  accessPoints: List[AccessPointF] = Nil
) extends Model with Persistable with Description
