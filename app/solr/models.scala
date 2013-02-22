package solr.models

import play.api.libs.concurrent.Execution.Implicits._
import com.github.seratch.scalikesolr.request.{QueryRequest => QRequest}
import com.github.seratch.scalikesolr.response.QueryResponse
import com.github.seratch.scalikesolr.request.query.{Query, FilterQuery, QueryParserType, Sort,StartRow,MaximumRowsReturned}
import com.github.seratch.scalikesolr.request.query.highlighting.{
    IsPhraseHighlighterEnabled, HighlightingParams}

import play.api.i18n
import play.api.libs.ws.{WS,Response}

import solr.facet.{FacetData,FacetClass,FieldFacetClass,QueryFacetClass,Facet}
import com.github.seratch.scalikesolr.request.query.facet.{FacetParams,FacetParam,Param,Value}
import concurrent.Future

/**
 * Helper for pagination.
 */

object SearchField extends Enumeration {
  type Field = Value
  val All = Value("all")
  val Title = Value("title")
  val Creator = Value("creator")
  val StartDate = Value("start_date")
}

object SearchOrder extends Enumeration {
  type Order = Value
  val Relevance = Value("relevance")
  val Title = Value("title")
  val DateNewest = Value("dateNewest")
  val DateOldest = Value("dateOldest")
}

object SearchType extends Enumeration {
  type Type = Value
  val All = Value("all")
  val Collection = Value("collection")
  val Authority = Value("authority")
  val Repository = Value("repository")
}


trait ItemPage[A] {
  val total: Long
  val page: Int
  val offset: Long
  val pageSize: Int
  val items: Seq[A]
  def numPages = (total / pageSize) + (total % pageSize).min(1)
  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)
}

case class Page[A](
  items: Seq[A], page: Int, offset: Long, pageSize:Int, total: Long, facets: List[FacetClass]) extends ItemPage[A]

case class FacetPage[A](
  fc: FacetClass, items: Seq[A], page: Int, offset: Long, pageSize: Int, total: Long) extends ItemPage[A]

object SolrHelper {

  private def setRequestFacets(request: QRequest, flist: List[FacetClass]): Unit = {
    request.setFacet(new FacetParams(
      enabled=true, 
      params=flist.map(_.asParams).flatten
    ))
  }

  private def setRequestFilters(request: QRequest, flist: List[FacetClass], applied: Map[String,Seq[String]]): Unit = {
    // filter the results by applied facets
    // NB: Scalikesolr is a bit dim WRT filter queries: you can
    // apparently only have one. So instead of adding multiple
    // fq clauses, we need to join them all with '+'
    val fqstring = flist.map(fclass => {
      applied.get(fclass.param).map(paramVals =>
        fclass match {
          case fc: FieldFacetClass => {
            paramVals.map("%s:\"%s\"".format(fc.key, _))
          }
          case fc: QueryFacetClass => {
            fc.facets.flatMap(facet => {
              if (paramVals.contains(facet.paramVal)) {
                List("%s:%s".format(fc.key, facet.solrVal))
              } else Nil
            })
          }
        }
      ).getOrElse(Nil)
    }).flatten.mkString(" +") // NB: Space before + is important
    request.setFilterQuery(FilterQuery(fqstring))
  }

  def constrain(request: QRequest, rtype: SearchType.Type, appliedFacets: Map[String,Seq[String]]): Unit = {
    val flist = FacetData.getForIndex(rtype)
    setRequestFacets(request, flist)
    setRequestFilters(request, flist, appliedFacets)
  }

  def extract(response: QueryResponse, rtype: SearchType.Type, appliedFacets: Map[String,Seq[String]]): List[FacetClass] = {
    val rawData = xml.XML.loadString(response.rawBody)
    FacetData.getForIndex(rtype).map(_.populateFromSolr(rawData, appliedFacets))
  }
  
  def buildQuery(index: SearchType.Type, offset: Int, pageSize: Int, orderBy: SearchOrder.Order,
        field: SearchField.Field, query: String, facets: Map[String, Seq[String]]): QRequest = {

    // Solr 3.6 seems to break querying with *:<query> style
    // http://bit.ly/MBKghG
    //val queryString = "%s:%s".format(
    //  if (field.trim == "") "*" else field,
    //  if (query.trim == "") "*" else query)
    val selector = field match {
      case SearchField.All => "*"
      case _ => field.toString
    }
    val queryString = "%s:%s".format(selector, if (query.trim.isEmpty) "*" else query.trim)

    val req: QRequest = new QRequest(Query(queryString))
    req.setFacet(new FacetParams(
      enabled=true, 
      params=List(new FacetParam(Param("facet.field"), Value("django_ct")))
    ))
    req.setQueryParserType(QueryParserType("edismax"))
    req.setHighlighting(HighlightingParams(
        enabled=true,
        isPhraseHighlighterEnabled=IsPhraseHighlighterEnabled(true)))

    orderBy match {
      case SearchOrder.Relevance => // This is the default!
      // TODO: Define these options more succinctly
      case SearchOrder.Title => req.setSort(Sort("title asc"))
      case SearchOrder.DateNewest => req.setSort(Sort("publication_date desc"))
      case SearchOrder.DateOldest => req.setSort(Sort("publication_date asc"))
      case _ => req.setSort(Sort("%s asc".format(orderBy)))
    }

    // Facet the request accordingly
    SolrHelper.constrain(req, index, facets)

    // if we're using a specific index, constrain on that as well
    index match {
      case SearchType.All =>
      case st =>
          req.setFilterQuery(
            FilterQuery(req.filterQuery.fq + " +django_ct:portal." + st.toString))
    }

    // Setup start and number of objects returned
    req.setStartRow(StartRow(offset))
    req.setMaximumRowsReturned(MaximumRowsReturned(pageSize))
    req
  }

  def buildSearchUrl(query: QRequest) = {
    "%s/select%s".format(
      play.Play.application.configuration.getString("solr.path"),
      query.queryString
    )
  }
}


object Description {
  def list(
    index: SearchType.Type = SearchType.All,
    page: Int = 1,
    pageSize: Int = 20,
    orderBy: SearchOrder.Order = SearchOrder.Relevance,
    field: SearchField.Field = SearchField.All,
    query: String = "",
    facets: Map[String, Seq[String]] = Map()
  
  ): Future[Page[Description]] = {
    val offset = (page - 1) * pageSize

    val queryreq = SolrHelper.buildQuery(
          index, offset, pageSize, orderBy, field, query, facets)

    WS.url(SolrHelper.buildSearchUrl(queryreq)).get.map { response =>
      val resp = new QueryResponse(writerType=queryreq.writerType, rawBody=response.body)
      val fclasses = SolrHelper.extract(resp, index, facets)

      // We only care about documents with the following content types,
      // so use a flatMap to extract them into the correct classes
      Page(resp.response.documents.flatMap(d => {
        d.get("type").toString match {
          case s: String => List(d.bind(classOf[Description]))
          case _ => Nil
        }
      }), page, offset, pageSize, resp.response.numFound, fclasses)
    }
  }
  
  def facet(
    facet: String,
    index: SearchType.Type = SearchType.All,
    page: Int = 1,
    pageSize: Int = 20,
    sort: String = "name",
    field: SearchField.Field = SearchField.All,
    query: String = "",
    facets: Map[String, Seq[String]] = Map()
  
  ): Future[FacetPage[Facet]] = {
    val offset = (page - 1) * pageSize

    // create a response returning 0 documents - we don't
    // actually care about the documents, so even this is
    // not strictly necessary... we also don't care about the
    // ordering.
    val queryreq = SolrHelper.buildQuery(
          index=index, offset=0, pageSize=0, orderBy=SearchOrder.Relevance,
          field=field, query=query, facets=facets)
    
    WS.url(SolrHelper.buildSearchUrl(queryreq)).get.map { response =>
      val resp = new QueryResponse(writerType=queryreq.writerType, rawBody=response.body)
      val fclasses = SolrHelper.extract(resp, index, facets)
    
      val fclass = fclasses.find(_.param==facet).getOrElse(
          throw new Exception("Unknown facet: " + facet))
      val flist = sort match {
        case "name" => fclass.sortedByName.slice(offset, offset + pageSize)
        case _ => fclass.sortedByCount.slice(offset, offset + pageSize)
      }
      FacetPage(fclass, flist, page, offset, pageSize, fclass.count)
    }
  }
}

abstract trait Description {
  var id: String
  var django_ct: String
  var name: String
  var slug: String
  var other_names: List[String]
  var text: String
  var publication_date: String
  var publication_status: Int
  var repository: String
  var repository_slug: String

  def index: String = django_ct.split("\\.")(1)
}








