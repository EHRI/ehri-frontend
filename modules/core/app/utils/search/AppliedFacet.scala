package utils.search

import play.api.libs.json.Json

/**
 * A facet that has been "applied", i.e. a name of the field
 * and the set of values that should be used to constrain
 * a particular search.
 */
case class AppliedFacet(name: String, values: List[String])

object AppliedFacet {
  implicit val _format = Json.format[AppliedFacet]
}

