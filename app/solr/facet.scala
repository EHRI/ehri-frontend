package solr.facet

import com.github.seratch.scalikesolr.request.query.facet.{FacetParam, Param, Value}


case object FacetSort extends Enumeration {
  val Name = Value("name")
  val Count = Value("count")
  val Fixed = Value("fixed")
}

/**
 * A facet that has been "applied", i.e. a name of the field
 * and the set of values that should be used to constrain
 * a particular search.
 * @param name
 * @param values
 */
case class AppliedFacet(name: String, values: List[String])

/**
 * Encapsulates a single facet.
 *
 * @param solrVal   the value of this facet to Solr
 * @param paramVal  the value as a web parameter
 * @param humanVal  the human-readable value
 * @param count     the number of objects to which this facet applies
 * @param applied   whether or not this facet is activated in the response
 */
case class Facet(
  solrVal: String,
  paramVal: String,
  humanVal: Option[String] = None,
  count: Int = 0,
  applied: Boolean = false
) {
  def sortVal = humanVal.getOrElse(paramVal)
}


/**
 * Encapulates rendering a facet to the response. Transforms
 * various Solr-internal values into i18n and human-readable ones.
 */
sealed trait FacetClass {
  val key: String
  val name: String
  val param: String
  val render: (String) => String
  val facets: List[Facet]
  val sort: FacetSort.Value
  val fieldType: String

  def count: Int = facets.length
  def sortedByName = facets.sortWith((a, b) => a.sortVal < b.sortVal)
  def sortedByCount = facets.sortWith((a, b) => b.count < a.count)
  def sorted: List[Facet] = sort match {
    case FacetSort.Name => sortedByName
    case FacetSort.Count => sortedByCount
    case _ => facets
  }
  def asParams: List[FacetParam]
  def pretty(f: Facet): String = f.humanVal match {
    case Some(desc) => render(desc)
    case None => render(f.paramVal)
  }
}

/**
 *
 * @param key     the name of the Solr field being faceted on
 * @param name    the 'pretty' human name of the Solr field
 * @param param   the name of the HTTP param used to apply this facet
 * @param render  a function (String => String) used to transform the
 *                facet values into human-readable ones, using, for
 *                example, i18n lookups.
 * @param facets  a list of individual Facet values
 * @param sort
 */
case class FieldFacetClass(
  key: String,
  name: String,
  param: String,
  render: (String) => String = s=>s,
  facets: List[Facet] = Nil,
  sort: FacetSort.Value = FacetSort.Count
) extends FacetClass {
  val fieldType: String = "facet.field"

  def asParams: List[FacetParam] = {
    List(new FacetParam(
      Param(fieldType),
      Value(key)
    ))
  }
}

case class QueryFacetClass(
  key: String,
  name: String,
  param: String,
  render: (String) => String = s=>s,
  facets: List[Facet] = Nil,
  sort: FacetSort.Value = FacetSort.Name
) extends FacetClass {
  val fieldType: String = "facet.query"

  def asParams: List[FacetParam] = {
    facets.map(p =>
      new FacetParam(
        Param(fieldType),
        Value("%s:%s".format(key, p.solrVal))
      )
    )
  }
}


