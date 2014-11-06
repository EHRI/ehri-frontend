package defines

object Fields extends Enumeration {
  type Name = Value
  
  val ID = Value("id")
  val IDENTIFIER = Value("identifier")
  val PUBLICATION = Value("publicationStatus")
  val NAME = Value("name")
  val TITLE = Value("title")
  val LANG_CODE = Value("languageCode")
  
  object DU extends Enumeration {
	type Name = Value
	
	val SCOPE_CONTENT = Value("scopeAndContent")
	val ACQUISITION = Value("acquisition")
	
  }
  
}