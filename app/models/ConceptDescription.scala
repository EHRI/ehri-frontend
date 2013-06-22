package models

import defines.EntityType
import models.base.Formable
import models.base.Description
import models.base.Persistable
import play.api.libs.json.{Format, Json}
import models.base.DescribedEntity
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
  prefLabel: String,
  altLabels: Option[List[String]] = None,
  definition: Option[List[String]] = None,
  scopeNote: Option[List[String]] = None
) extends Persistable


case class ConceptDescription(val e: Entity)
  extends Description
  with Formable[ConceptDescriptionF] {

  lazy val item: Option[Concept] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(Concept(_))

  def formable: ConceptDescriptionF = Json.toJson(e).as[ConceptDescriptionF]
  def formableOpt: Option[ConceptDescriptionF] = Json.toJson(e).asOpt[ConceptDescriptionF]
}
