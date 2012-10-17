package defines

object AuthorityType extends Enumeration {
	type Type = Value
	val Person = Value("Person")
	val Family = Value("Family")
	val CorporateBody = Value("Corporate Body")
}