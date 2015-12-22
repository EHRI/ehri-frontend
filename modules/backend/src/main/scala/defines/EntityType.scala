package defines

import eu.ehri.project.definitions.Entities._

object EntityType extends Enumeration {
  // Allow these enums to be converted to strings implicitly,
  // since the binding relies on to/from string behaviour.
  import scala.language.implicitConversions
  implicit def enumToString(e: Enumeration#Value): String = e.toString

  type Type = Value
  val DocumentaryUnit = Value(DOCUMENTARY_UNIT)
  val DocumentaryUnitDescription = Value(DOCUMENTARY_UNIT_DESCRIPTION)
  val Repository = Value(REPOSITORY)
  val RepositoryDescription = Value(REPOSITORY_DESCRIPTION)
  val HistoricalAgent = Value(HISTORICAL_AGENT)
  val HistoricalAgentDescription = Value(HISTORICAL_AGENT_DESCRIPTION)
  val SystemEvent = Value(SYSTEM_EVENT)
  val UserProfile = Value(USER_PROFILE)
  val Group = Value(GROUP)
  val ContentType = Value(CONTENT_TYPE)
  val DatePeriod = Value(DATE_PERIOD)
  val Address = Value(ADDRESS)
  val PermissionGrant = Value(PERMISSION_GRANT)
  val Permission = Value(PERMISSION)
  val Annotation = Value(ANNOTATION)
  val Concept = Value(CVOC_CONCEPT)
  val ConceptDescription = Value(CVOC_CONCEPT_DESCRIPTION)
  val Vocabulary = Value(CVOC_VOCABULARY)
  val AuthoritativeSet = Value(AUTHORITATIVE_SET)
  val AccessPoint = Value(ACCESS_POINT)
  val Link = Value(LINK)
  val Country = Value(COUNTRY)
  val UnknownProperty = Value(UNKNOWN_PROPERTY)
  val MaintenanceEvent = Value(MAINTENANCE_EVENT)
  val VirtualUnit = Value(VIRTUAL_UNIT)
  val Version = Value(VERSION)

  implicit val _format = defines.EnumUtils.enumFormat(this)
}
