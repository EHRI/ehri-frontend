package utils

import defines.EntityType
import models.Isaar
import solr.SolrConstants
import utils.search.{Facet, FacetClass}

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
  private def typeCount(facets: List[FacetClass[Facet]], key: String, facetName: Any)
  = facets.find(_.key == key).flatMap(_.facets.find(_.value == facetName.toString).map(_.count)).getOrElse(0)

  /**
   * Extract the total number of facets for a given class.
   */
  private def allCount(facets: List[FacetClass[Facet]], key: String)
  = facets.find(_.key == key).map(_.count).getOrElse(0)

  /**
   * Construct a Stats value from a list of facets.
   */
  def apply(facets: List[FacetClass[Facet]]): Stats = new Stats(
    countryCount = typeCount(facets, SolrConstants.TYPE, EntityType.Country),
    repositoryCount = typeCount(facets, SolrConstants.TYPE, EntityType.Repository),
    inCountryCount = allCount(facets, SolrConstants.COUNTRY_CODE),
    documentaryUnitCount = typeCount(facets, SolrConstants.TYPE, EntityType.DocumentaryUnit),
    inRepositoryCount = allCount(facets, SolrConstants.HOLDER_NAME),
    historicalAgentCount = typeCount(facets, SolrConstants.TYPE, EntityType.HistoricalAgent),
    corpCount = typeCount(facets, Isaar.ENTITY_TYPE, Isaar.HistoricalAgentType.CorporateBody),
    personCount = typeCount(facets, Isaar.ENTITY_TYPE, Isaar.HistoricalAgentType.Person),
    familyCount = typeCount(facets, Isaar.ENTITY_TYPE, Isaar.HistoricalAgentType.Family)
  )
}


