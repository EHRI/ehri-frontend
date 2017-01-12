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

private case class FacetInfo(
  inRepositories: Int,
  inCountries: Int,
  totals: Map[EntityType.Value, Int]
)

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
    val lookupReader: Reads[Seq[BucketStat]] = Reads { json =>
      (json \ "buckets").validate[Seq[BucketStat]]
    }

    val parsed = FacetInfo(
      inRepositories = info.get("inRepositories").flatMap {
        case JsNumber(num) => Some(num.toIntExact)
      }.getOrElse(0),
      inCountries = info.get("inCountries").flatMap {
        case JsNumber(num) => Some(num.toIntExact)
      }.getOrElse(0),
      totals = info.get("totals").flatMap {
        case js: JsValue => js.validate(lookupReader).asOpt
          .map(bs =>  bs.map(b => b.`val` -> b.count).toMap)
      }.getOrElse {
        println("FAILED")
        Map.empty
      }
    )

    Stats(
      countryCount = parsed.totals.getOrElse(EntityType.Country, 0),
      repositoryCount = parsed.totals.getOrElse(EntityType.Repository, 0),
      documentaryUnitCount = parsed.totals.getOrElse(EntityType.DocumentaryUnit, 0),
      inRepositoryCount = parsed.inRepositories,
      inCountryCount = parsed.inCountries
    )
  }

  /**
    * Extract the count of a particular facet within the given class.
    */
  private def typeCount(facets: Seq[FacetClass[Facet]], key: String, facetName: Any): Int =
  facets.find(_.key == key).flatMap(_.facets.find(_.value == facetName.toString).map(_.count)).getOrElse(0)

  /**
    * Extract the total number of facets for a given class.
    */
  private def allCount(facets: Seq[FacetClass[Facet]], key: String): Int =
  facets.find(_.key == key).flatMap(_.total).getOrElse(0)

  /**
    * Construct a Stats value from a list of facets.
    */
  def apply(facets: Seq[FacetClass[Facet]]): Stats = new Stats(
    countryCount = typeCount(facets, SearchConstants.TYPE, EntityType.Country),
    repositoryCount = typeCount(facets, SearchConstants.TYPE, EntityType.Repository),
    inCountryCount = allCount(facets, SearchConstants.COUNTRY_CODE),
    documentaryUnitCount = typeCount(facets, SearchConstants.TYPE, EntityType.DocumentaryUnit),
    inRepositoryCount = allCount(facets, SearchConstants.HOLDER_ID)
  )
}


