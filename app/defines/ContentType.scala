package defines


// FIXME: Remove this duplication with the
// entity types enum
object ContentType extends Enumeration() {
  type Type = Value
  val DocumentaryUnit = Value("documentaryUnit")
  val Agent = Value("agent")
  val ActionLog = Value("action")
  val Authority = Value("authority")
  val UserProfile = Value("userProfile")
  val Group = Value("group")
  val Annotation = Value("annotation")
  val CvocConcept = Value("cvocConcept")
  val CvocVocabulary = Value("cvocVocabulary")
}
