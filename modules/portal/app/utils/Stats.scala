package utils

import defines.EntityType
import play.api.libs.json._

case class Stats(
  countryCount: Int,
  repositoryCount: Int,
  documentaryUnitCount: Int,
  inCountryCount: Int,
  inRepositoryCount: Int
)

object Stats {
  val analyticsQuery: String = Json.stringify(
    Json.obj(
      "inRepositories" -> "unique(repositoryId)",
      "inCountries" -> "unique(countryCode)",
      "totals" -> Json.obj(
        "terms" -> Json.obj(
          "field" -> "type"
        )
      )
    )
  )

  def apply(info: Map[String, Any]): Stats = {
    // This is somewhat nasty since it relies on the JSON facetting syntax
    // in Solr 5.2 and above, and the peculiar way Solr returns JSON responses.
    def getTotal(et: EntityType.Value): Option[Int] = info.get("totals").flatMap {
      case js: JsValue => (js \ "buckets").as[Seq[JsObject]]
        .find(_.values.toList.contains(JsString(et.toString)))
        .flatMap(_.fields.collectFirst { case ("count", JsNumber(n)) => n.toIntExact })
    }

    def getNum(key: String): Option[Int] = info.get(key)
      .collect { case JsNumber(num) => num.toInt }

    Stats(
      countryCount = getTotal(EntityType.Country).getOrElse(0),
      repositoryCount = getTotal(EntityType.Repository).getOrElse(0),
      documentaryUnitCount = getTotal(EntityType.DocumentaryUnit).getOrElse(0),
      inRepositoryCount = getNum("inRepositories").getOrElse(0),
      inCountryCount = getNum("inCountries").getOrElse(0)
    )
  }
}


