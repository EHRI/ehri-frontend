package defines

import eu.ehri.project.definitions.Entities

object ContentTypes extends Enumeration() {
  type Type = Value
  val HistoricalAgent = Value(Entities.HISTORICAL_AGENT)
  val DocumentaryUnit = Value(Entities.DOCUMENTARY_UNIT)
  val Repository = Value(Entities.REPOSITORY)
  val SystemEvent = Value(Entities.SYSTEM_EVENT)
  val UserProfile = Value(Entities.USER_PROFILE)
  val Group = Value(Entities.GROUP)
  val Annotation = Value(Entities.ANNOTATION)
  val Concept = Value(Entities.CVOC_CONCEPT)
  val Vocabulary = Value(Entities.CVOC_VOCABULARY)
  val AuthoritativeSet = Value(Entities.AUTHORITATIVE_SET)
  val Link = Value(Entities.LINK)
  val Country = Value(Entities.COUNTRY)
  val VirtualUnit = Value(Entities.VIRTUAL_UNIT)

  implicit val _format = defines.EnumUtils.enumFormat(this)
}
