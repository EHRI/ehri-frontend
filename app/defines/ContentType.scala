package defines


// FIXME: Remove this duplication with the
// entity types enum
object ContentType extends Enumeration() {
  type Type = Value
  val DocumentaryUnit = Value("documentaryUnit")
  val Agent = Value("agent")
  val SystemEvent = Value("systemEvent")
  val Authority = Value("authority")
  val UserProfile = Value("userProfile")
  val Group = Value("group")
  val Annotation = Value("annotation")
  val Concept = Value("cvocConcept")
  val Vocabulary = Value("cvocVocabulary")
}
