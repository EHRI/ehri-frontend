package utils

import defines.EntityType
import models.Isaar
import utils.search.{SearchConstants, Facet, FacetClass}

/**
 * Class for holding useful stats for the data in our system.
 */
case class Stats(
  countryCount: Int,
  repositoryCount: Int,
  inCountryCount: Int,
  documentaryUnitCount: Int,
  inRepositoryCount: Int,
  historicalAgentCount: Int,
  corpCount: Int,
  personCount: Int,
  familyCount: Int
)

object Stats {

  /**
   * Extract the count of a particular facet within the given class.
   */
  private def typeCount(facets: Seq[FacetClass[Facet]], key: String, facetName: Any): Int =
    facets.find(_.key == key).flatMap(_.facets.find(_.value == facetName.toString).map(_.count)).getOrElse(0)

  /**
   * Extract the total number of facets for a given class.
   */
  private def allCount(facets: Seq[FacetClass[Facet]], key: String): Int =
    facets.find(_.key == key).map(_.count).getOrElse(0)

  /**
   * Construct a Stats value from a list of facets.
   */
  def apply(facets: Seq[FacetClass[Facet]]): Stats = new Stats(
    countryCount = typeCount(facets, SearchConstants.TYPE, EntityType.Country),
    repositoryCount = typeCount(facets, SearchConstants.TYPE, EntityType.Repository),
    inCountryCount = allCount(facets, SearchConstants.COUNTRY_CODE),
    documentaryUnitCount = typeCount(facets, SearchConstants.TYPE, EntityType.DocumentaryUnit),
    inRepositoryCount = allCount(facets, SearchConstants.HOLDER_ID),
    historicalAgentCount = typeCount(facets, SearchConstants.TYPE, EntityType.HistoricalAgent),
    corpCount = typeCount(facets, Isaar.ENTITY_TYPE, Isaar.HistoricalAgentType.CorporateBody),
    personCount = typeCount(facets, Isaar.ENTITY_TYPE, Isaar.HistoricalAgentType.Person),
    familyCount = typeCount(facets, Isaar.ENTITY_TYPE, Isaar.HistoricalAgentType.Family)
  )
}


