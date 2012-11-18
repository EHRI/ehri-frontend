package models.base



object Field {
	implicit def fieldToString(f: Field): String = f.toString
}

case class Field(val id: String, val name: String, val code: Option[String] = None) {
  override def toString = id
}