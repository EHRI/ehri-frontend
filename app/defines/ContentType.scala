package defines


// FIXME: Remove this duplication with the
// entity types enum
object ContentType extends Enumeration() {
  type Type = Value
  val Actor = Value("authority")
  val DocumentaryUnit = Value("documentaryUnit")
  val Repository = Value("agent")
  val SystemEvent = Value("systemEvent")
  val UserProfile = Value("userProfile")
  val Group = Value("group")
  val Annotation = Value("annotation")
  val Concept = Value("cvocConcept")
  val Vocabulary = Value("cvocVocabulary")
}
