package defines


// FIXME: Remove this duplication with the
// entity types enum
object ContentType extends Enumeration() {
  type Type = Value
  val HistoricalAgent = Value("historicalAgent")
  val DocumentaryUnit = Value("documentaryUnit")
  val Repository = Value("repository")
  val SystemEvent = Value("systemEvent")
  val UserProfile = Value("userProfile")
  val Group = Value("group")
  val Annotation = Value("annotation")
  val Concept = Value("cvocConcept")
  val Vocabulary = Value("cvocVocabulary")
  val AuthoritativeSet = Value("authoritativeSet")
  val Link = Value("link")
}
