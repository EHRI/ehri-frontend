package solr

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */

import play.api.Logger
import utils.search._


/**
 * Encapsulates a single facet.
 *
 * @param solr   the value of this facet to Solr
 * @param value  the value as a web parameter
 * @param name  the human-readable value
 * @param count     the number of objects to which this facet applies
 * @param applied   whether or not this facet is activated in the response
 */
case class SolrFieldFacet(
  solr: String,
  value: String,
  name: Option[String] = None,
  count: Int = 0,
  applied: Boolean = false
) extends Facet {
  def solrValue = solr
}

case class SolrQueryFacet(
  count: Int = 0,
  applied: Boolean = false,
  name: Option[String] = None,
  value: String,
  range: QueryPoint
) extends Facet {
  def solrValue: String = range match {
    case r: QueryRange => r.points.toList match {
      case Glob :: Point(p) :: Nil => s"[* TO $p]"
      case Point(p1) :: Point(p2) :: Nil => s"[$p1 TO $p2]"
      case Point(p) :: Glob :: Nil => s"[$p TO *]"
      case Point(p) :: Nil => p.toString
      case p =>
        Logger.warn(s"Unsupported facet class points: $r -> $p")
        "*"
    }
    case Glob => "*"
    case Point(p) => p.toString
  }
}


