package solr

import play.api.libs.concurrent.Execution.Implicits._
import com.github.seratch.scalikesolr.request.QueryRequest
import com.github.seratch.scalikesolr.response.QueryResponse
import models.UserProfile
import play.api.libs.ws.WS
import play.api.Logger
import xml.{Node,NodeSeq}
import concurrent.Future
import defines.EntityType
import rest.RestError
import solr.facet.{FacetClass, AppliedFacet}


object SolrDispatcher {
  val test = """<response>
    <lst name="responseHeader">
      <int name="status">0</int>
      <int name="QTime">83</int>
    </lst>
    <lst name="grouped">
      <lst name="itemId">
        <int name="matches">4198</int>
      </lst>
    </lst>
  </response>
             """
}


/**
 * Class for fetching query results from a Solr instance.
 * @param userProfile
 */
case class SolrDispatcher(userProfile: Option[UserProfile]) extends rest.RestDAO {

  def attributeValueEquals(value: String)(node: Node) = {
    node.attributes.exists(_.value.text == value)
  }

  def itemsFromXml(nodes: NodeSeq): Seq[SearchDescription] = (nodes \ "lst" \ "lst" \ "result" \ "doc").map { doc =>
    SearchDescription(
      id = (doc \\ "str").filter(attributeValueEquals("id")).text,
      name = (doc \\ "str").filter(attributeValueEquals("name")).text,
      `type` = EntityType.withName((doc \\ "str").filter(attributeValueEquals("type")).text),
      itemId = (doc \\ "str").filter(attributeValueEquals("itemId")).text,
      data = (doc \\ "str").foldLeft(Map[String,String]()) { (m, node) =>
        node.attributes.get("name").map { attr =>
          m + (attr.head.toString -> node.text)
        } getOrElse m
      }
    )
  }

  def numFound(nodes: NodeSeq): Int = {
    val s = (nodes \ "lst" \ "lst" \ "int").filter(attributeValueEquals("ngroups")).text
    try {
      s.toInt
    } catch {
      case e: NumberFormatException => 0
    }
  }

  def list(params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass]): Future[Either[RestError,ItemPage[SearchDescription]]] = {
    val limit = params.limit.getOrElse(20)
    val offset = (Math.max(params.page.getOrElse(1), 1) - 1) * limit

    val queryRequest = SolrHelper.buildQuery(params, facets, allFacets)(userProfile)
    Logger.logger.debug(queryRequest.queryString())

    WS.url(SolrHelper.buildSearchUrl(queryRequest)).get.map { response =>
      checkError(response).right.map { r =>
        val facetClasses = SolrHelper.extract(response.body, facets, allFacets)
        val nodes = xml.XML.loadString(r.body)

        ItemPage(itemsFromXml(nodes), offset, limit, numFound(nodes), facetClasses)
      }
    }
  }

  def facet(
    facet: String,
    sort: String = "name",
    params: SearchParams,
    facets: List[AppliedFacet],
    allFacets: List[FacetClass]
  ): Future[Either[RestError,solr.FacetPage[solr.facet.Facet]]] = {
    val limit = params.limit.getOrElse(20)
    val offset = (Math.max(params.page.getOrElse(1), 1) - 1) * limit

    // create a response returning 0 documents - we don't
    // actually care about the documents, so even this is
    // not strictly necessary... we also don't care about the
    // ordering.
    val queryRequest = SolrHelper.buildQuery(params, facets, allFacets)(userProfile)

    WS.url(SolrHelper.buildSearchUrl(queryRequest)).get.map { response =>
      checkError(response).right.map { r =>
        val facetClasses = SolrHelper.extract(response.body, facets, allFacets)

        val facetClass = facetClasses.find(_.param==facet).getOrElse(
            throw new Exception("Unknown facet: " + facet))
        val facetLabels = sort match {
          case "name" => facetClass.sortedByName.slice(offset, offset + limit)
          case _ => facetClass.sortedByCount.slice(offset, offset + limit)
        }
        FacetPage(facetClass, facetLabels, offset, limit, facetClass.count)
      }
    }
  }
}
