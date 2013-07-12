package solr.facet

import com.github.seratch.scalikesolr.request.query.facet.{FacetParam, Param, Value}
import play.api.libs.json.{JsNumber, Json, Writes}
import utils.search.{FacetClass, FacetSort}

/**
 * A facet that has been "applied", i.e. a name of the field
 * and the set of values that should be used to constrain
 * a particular search.
 * @param name
 * @param values
 */
case class AppliedFacet(name: String, values: List[String])

object AppliedFacet {
  implicit val appliedFacetWrites = Json.writes[AppliedFacet]
}


/**
 * Encapsulates a single facet.
 *
 * @param solr   the value of this facet to Solr
 * @param param  the value as a web parameter
 * @param name  the human-readable value
 * @param count     the number of objects to which this facet applies
 * @param applied   whether or not this facet is activated in the response
 */
case class SolrFacet(
  solr: String,
  param: String,
  name: Option[String] = None,
  count: Int = 0,
  applied: Boolean = false
) extends utils.search.Facet {
  def sort = name.getOrElse(param)
}

object SolrFacet {
  implicit val facetWrites = Json.writes[SolrFacet]
}


trait SolrFacetClass extends FacetClass[SolrFacet] {
  def asParams: List[FacetParam]
}

object SolrFacetClass {
  implicit def facetClassWrites: Writes[SolrFacetClass] = new Writes[SolrFacetClass] {
    def writes(fc: SolrFacetClass) = Json.obj(
      "count" -> JsNumber(fc.count),
      "param" -> Json.toJson(fc.param),
      "name" -> Json.toJson(fc.name),
      "key" -> Json.toJson(fc.key),
      "facets" -> Json.arr(
        fc.sorted.map(Json.toJson(_))
      )
    )
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
  facets: List[SolrFacet] = Nil,
  sort: FacetSort.Value = FacetSort.Count
) extends SolrFacetClass {
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
  facets: List[SolrFacet] = Nil,
  sort: FacetSort.Value = FacetSort.Name
) extends SolrFacetClass {
  val fieldType: String = "facet.query"

  def asParams: List[FacetParam] = {
    facets.map(p =>
      new FacetParam(
        Param(fieldType),
        Value("%s:%s".format(key, p.solr))
      )
    )
  }
}


