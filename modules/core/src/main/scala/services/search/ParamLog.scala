package services.search

/*
 * Class to aid in debugging the last submitted request - gross...
 */
case class ParamLog(
  params: SearchParams,
  facets: Seq[AppliedFacet],
  allFacets: Seq[FacetClass[Facet]],
  filters: Map[String, Any] = Map.empty
)
