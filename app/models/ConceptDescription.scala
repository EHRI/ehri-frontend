package models

import defines.EntityType
import models.base.Formable
import models.base.Description
import models.base.Persistable
import play.api.libs.json.{Format, Json}
import models.base.DescribedEntity

object ConceptDescriptionF {
  lazy implicit val conceptDescriptionFormat: Format[ConceptDescriptionF] = json.ConceptDescriptionFormat.restFormat
}

case class ConceptDescriptionF(
  val id: Option[String],
  val languageCode: String,
  val prefLabel: String,
  val altLabels: Option[List[String]] = None,
  val definition: Option[List[String]] = None,
  val scopeNote: Option[List[String]] = None
) extends Persistable {
  val isA = EntityType.ConceptDescription
}


case class ConceptDescription(val e: Entity)
  extends Description
  with Formable[ConceptDescriptionF] {

  lazy val item: Option[Concept] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(Concept(_))

  def formable: ConceptDescriptionF = Json.toJson(e).as[ConceptDescriptionF]
  def formableOpt: Option[ConceptDescriptionF] = Json.toJson(e).asOpt[ConceptDescriptionF]
}
