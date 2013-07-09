package defines

object PublicationStatus extends Enumeration {
	type Status = Value
	val Published = Value("Published")
	val Draft = Value("Draft")

  implicit val format = defines.EnumUtils.enumFormat(this)
}