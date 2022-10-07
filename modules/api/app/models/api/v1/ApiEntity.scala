package models.api.v1

import models.EntityType
import play.api.mvc.QueryStringBindable

object ApiEntity extends Enumeration {

  val DocumentaryUnit = Value(EntityType.DocumentaryUnit.toString)
  val VirtualUnit = Value(EntityType.VirtualUnit.toString)
  val Repository = Value(EntityType.Repository.toString)
  val HistoricalAgent = Value(EntityType.HistoricalAgent.toString)
  val Country = Value(EntityType.Country.toString)
  val CvocConcept = Value(EntityType.Concept.toString)

  def asEntityTypes: Seq[EntityType.Value] = values.toSeq.map(toEntityType)
  def toEntityType(e: ApiEntity.Value): EntityType.Value = EntityType.withName(e.toString)

  implicit val _qsb: QueryStringBindable[ApiEntity.Value] = utils.binders.queryStringBinder(this)
}
