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
import solr.facet.AppliedFacet


object SolrDispatcher {
}

/**
 * Class for fetching query results from a Solr instance.
 * @param userProfile
 */
case class SolrDispatcher(userProfile: Option[UserProfile]) extends rest.RestDAO {
  def itemsFromXml(nodes: NodeSeq): Seq[SearchDescription] = (nodes \\ "doc").map { doc =>
    def attributeValueEquals(value: String)(node: Node) = {
      node.attributes.exists(_.value.text == value)
    }

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

  def list(params: SearchParams, facets: List[AppliedFacet]): Future[Either[RestError,ItemPage[SearchDescription]]] = {
    val limit = params.limit.getOrElse(20)
    val offset = (Math.max(params.page.getOrElse(1), 1) - 1) * limit

    val queryRequest = SolrHelper.buildQuery(params, facets)
    Logger.logger.debug(queryRequest.queryString())

    WS.url(SolrHelper.buildSearchUrl(queryRequest)).get.map { response =>
      checkError(response).right.map { r =>
        val resp = new QueryResponse(writerType=queryRequest.writerType, rawBody=r.body)
        val facetClasses = SolrHelper.extract(resp, params.entity, facets)
        val nodes = xml.XML.loadString(r.body)

        ItemPage(itemsFromXml(nodes), offset, limit, resp.response.numFound, facetClasses)
      }
    }
  }

  def facet(
    facet: String,
    sort: String = "name",
    params: SearchParams,
    facets: List[AppliedFacet]): Future[Either[RestError,solr.FacetPage[solr.facet.Facet]]] = {
    val limit = params.limit.getOrElse(20)
    val offset = (Math.max(params.page.getOrElse(1), 1) - 1) * limit

    // create a response returning 0 documents - we don't
    // actually care about the documents, so even this is
    // not strictly necessary... we also don't care about the
    // ordering.
    val queryRequest = SolrHelper.buildQuery(params, facets)

    WS.url(SolrHelper.buildSearchUrl(queryRequest)).get.map { response =>
      checkError(response).right.map { r =>
        val resp = new QueryResponse(writerType=queryRequest.writerType, rawBody=r.body)
        val facetClasses = SolrHelper.extract(resp, params.entity, facets)

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
