package solr.facet

import java.util.Locale

import com.github.seratch.scalikesolr.request.query.facet.{FacetParams, FacetParam, Param, Value}
import defines.EntityType
import views.Helpers
import play.api.i18n.{Lang, Messages}
import play.api.mvc.{AnyContent, Request}

object Utils {

  def joinPath(path: String, qs: Map[String, Seq[String]]): String = {
    List(path, joinQueryString(qs)).filterNot(_=="").mkString("?")
  }

  def joinQueryString(qs: Map[String, Seq[String]]): String = {
    import java.net.URLEncoder
    qs.map { case (key, vals) => {
      vals.map(v => "%s=%s".format(key, URLEncoder.encode(v, "UTF-8")))    
    }}.flatten.mkString("&")
  }

  def pathWithoutFacet(fc: FacetClass, f: Facet, path: String, qs: Map[String, Seq[String]]): String = {
    joinPath(path, qs.map(qv => {
      qv._1 match {
        case fc.param => (qv._1, qv._2.filter(_!=f.paramVal))
        case _ => qv
      }
    }))
  }

  def pathWithFacet(fc: FacetClass, f: Facet, path: String, qs: Map[String, Seq[String]]): String = {
    joinPath(path, if (qs.contains(fc.param)) {
        qs.map(qv => {
          qv._1 match {
            case fc.param => (qv._1, qv._2.union(Seq(f.paramVal)).distinct)
            case _ => qv
          }
        })
      } else qs.updated(fc.param, Seq(f.paramVal))
    )
  }
}


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
  val solrVal: String,
  val paramVal: String,
  val humanVal: Option[String] = None,
  val count: Int = 0,
  val applied: Boolean = false
) {
  def sortVal = humanVal.getOrElse(paramVal)
}


/**
 * Encapulates rendering a facet to the response. Transforms
 * various Solr-internal values into i18n and human-readable ones.
 *
 * @param key     the name of the Solr field being faceted on
 * @param name    the 'pretty' human name of the Solr field
 * @param param   the name of the HTTP param used to apply this facet
 * @param render  a function (String => String) used to transform the
 *                facet values into human-readable ones, using, for
 *                example, i18n lookups.
 * @param facets  a list of individual Facet values
 */
sealed abstract class FacetClass (
  val key: String,
  val name: String,
  val param: String,
  val render: (String) => String = s => s,
  private val facets: List[Facet] = Nil,
  val sort: FacetSort.Value = FacetSort.Count
) {
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
  
  def populateFromSolr(data: xml.Elem, current: List[AppliedFacet]): FacetClass
  
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
  override val key: String,
  override val name: String,
  override val param: String,
  override val render: (String) => String = s=>s,
  val facets: List[Facet] = Nil,
  override val sort: FacetSort.Value = FacetSort.Count
) extends FacetClass(key,name, param,render,facets,sort) {
  override val fieldType: String = "facet.field"
  
  def asParams: List[FacetParam] = {
    List(new FacetParam(
      Param(fieldType),
      Value(key)
    ))      
  }
  
  override def populateFromSolr(data: xml.Elem, current: List[AppliedFacet]): FacetClass = {
    val applied: List[String] = current.filter(_.name == key).headOption.map(_.values).getOrElse(List[String]())
    val nodes = data.descendant.filter(n => (n \ "@name").text == "facet_fields") 
    var facets = List[Facet]()
    if (nodes.length > 0) {
      val my = nodes.head.descendant.filter(n => (n \ "@name").text == key)
      my.head.descendant.foreach(n => {
        val name = n \ "@name"
        if (name.length > 0) {
          facets = facets ::: List(Facet(name.text, name.text, None, n.text.toInt, applied.contains(name.text)))
        }
      })
    }
    FieldFacetClass(key, name, param, render, facets, sort)  
  }
}

case class QueryFacetClass(
  override val key: String,
  override val name: String,
  override val param: String,
  override val render: (String) => String = s=>s,
  facets: List[Facet] = Nil,
  override val sort: FacetSort.Value = FacetSort.Name
) extends FacetClass(key,name,param,render,facets,sort) {
  override val fieldType: String = "facet.query"
  
  def asParams: List[FacetParam] = {
    facets.map(p =>
      new FacetParam(
        Param(fieldType),
        Value("%s:%s".format(key, p.solrVal))
      )
    )      
  }
  
  override def populateFromSolr(data: xml.Elem, current: List[AppliedFacet]): FacetClass = {
    val applied: List[String] = current.filter(_.name == key).headOption.map(_.values).getOrElse(List[String]())
    val popfacets = facets.flatMap(f => {
      var nameval = "%s:%s".format(key, f.solrVal)
      data.descendant.filter(n => (n \\ "@name").text == nameval).text match {
        case "" => Nil
        case v => List(
          Facet(f.solrVal, f.paramVal, f.humanVal, v.toInt,
            applied.contains(f.paramVal))
        )
      }
    })
    QueryFacetClass(key, name, param, render, popfacets, sort)
  }
}


