package models

import defines.EntityType
import models.base._
import play.api.libs.json.{Format, Json}
import models.json.{ClientConvertable, RestConvertable}

object ConceptDescriptionF {
  lazy implicit val conceptDescriptionFormat: Format[ConceptDescriptionF] = json.ConceptDescriptionFormat.restFormat

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
  accessPoints: List[AccessPointF] = None
) extends Model with Persistable with Description


/*
case class ConceptDescription(val e: Entity)
  extends Description
  with Formable[ConceptDescriptionF] {

  lazy val item: Option[Concept] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(Concept(_))

  def formable: ConceptDescriptionF = Json.toJson(e).as[ConceptDescriptionF]
  def formableOpt: Option[ConceptDescriptionF] = Json.toJson(e).asOpt[ConceptDescriptionF]
}
*/
