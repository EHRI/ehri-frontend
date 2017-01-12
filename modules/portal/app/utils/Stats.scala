package utils

import defines.EntityType
import play.api.libs.json._
import utils.search.{Facet, FacetClass, SearchConstants}

/**
  * Class for holding useful stats for the data in our system.
  */
case class Stats(
  countryCount: Int = 0,
  repositoryCount: Int = 0,
  inCountryCount: Int = 0,
  documentaryUnitCount: Int = 0,
  inRepositoryCount: Int = 0
)

private case class BucketStat(
  `val`: EntityType.Value,
  count: Int
)
private object BucketStat {
  implicit val reads = Json.reads[BucketStat]
}

object Stats {

  val query = Json.stringify(
    Json.obj(
      "inRepositories" -> "unique(holderId)",
      "inCountries" -> "unique(countryCode)",
      "totals" -> Json.obj(
        "terms" -> Json.obj(
          "field" -> "type"
        )
      )
    )
  )

  def apply(info: Map[String, Any]): Stats = {
    val totals: Map[defines.EntityType.Value, Int] = info.get("totals").flatMap {
      case js: JsValue => (js \ "buckets").validate[Seq[BucketStat]].asOpt
        .map(bs => bs.map(b => b.`val` -> b.count).toMap)
    }.getOrElse(Map.empty)

    Stats(
      countryCount = totals.getOrElse(EntityType.Country, 0),
      repositoryCount = totals.getOrElse(EntityType.Repository, 0),
      documentaryUnitCount = totals.getOrElse(EntityType.DocumentaryUnit, 0),
      inRepositoryCount = info.get("inRepositories")match {
        case Some(JsNumber(num)) => num.toIntExact
        case _ => 0
      },
      inCountryCount = info.get("inCountries") match {
        case Some(JsNumber(num)) => num.toIntExact
        case _ => 0
      }
    )
  }
}


