package defines

object EntityType extends Enumeration() {
  type Type = Value
  val DocumentaryUnit = Value("documentaryUnit")
  val Agent = Value("agent")
  val Authority = Value("authority")
  val ActionLog = Value("action")
  val UserProfile = Value("userProfile")
  val Group = Value("group")
  val ContentType = Value("contentType")
  val DocumentaryUnitDescription = Value("documentDescription")
  val AgentDescription = Value("agentDescription")
  val AuthorityDescription = Value("authorityDescription")
  val DatePeriod = Value("datePeriod")
  val Address = Value("address")
  val PermissionGrant = Value("permissionGrant")
  val Permission = Value("permission")
  val Annotation = Value("annotation")
  val Concept = Value("cvocConcept")
  val ConceptDescription = Value("cvocConceptDescription")
  val Vocabulary = Value("cvocVocabulary")
}
