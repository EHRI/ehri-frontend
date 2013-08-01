package defines

object EntityType extends Enumeration() {
  type Type = Value
  val DocumentaryUnit = Value("documentaryUnit")
  val DocumentaryUnitDescription = Value("documentDescription")
  val Repository = Value("repository")
  val RepositoryDescription = Value("repositoryDescription")
  val HistoricalAgent = Value("historicalAgent")
  val HistoricalAgentDescription = Value("historicalAgentDescription")
  val SystemEvent = Value("systemEvent")
  val UserProfile = Value("userProfile")
  val Group = Value("group")
  val ContentType = Value("contentType")
  val DatePeriod = Value("datePeriod")
  val Address = Value("address")
  val PermissionGrant = Value("permissionGrant")
  val Permission = Value("permission")
  val Annotation = Value("annotation")
  val Concept = Value("cvocConcept")
  val ConceptDescription = Value("cvocConceptDescription")
  val Vocabulary = Value("cvocVocabulary")
  val AuthoritativeSet = Value("authoritativeSet")
  val AccessPoint = Value("relationship")
  val Link = Value("link")
  val Country = Value("country")
  val UnknownProperty = Value("property")

  implicit val format = defines.EnumUtils.enumFormat(this)
}
