package eu.ehri.project.xml

import play.api.libs.json.{Json, Writes}

case class XsltConfigError(
  error: String
) extends Exception(error)

object XsltConfigError {
  implicit val _writes: Writes[XsltConfigError] = Json.writes[XsltConfigError]
}


