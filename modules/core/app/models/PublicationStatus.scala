package models

import play.api.libs.json.Format

object PublicationStatus extends Enumeration {
	type Status = Value
	val Draft = Value("Draft")
  val Published = Value("Published")

  implicit val _format: Format[PublicationStatus.Value] = defines.EnumUtils.enumFormat(this)
}