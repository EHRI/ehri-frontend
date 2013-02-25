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



object SolrDispatcher {
}


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

  def list(params: SearchParams): Future[Either[RestError,ItemPage[SearchDescription]]] = {
    val offset = (params.page - 1) * params.limit

    val queryRequest = SolrHelper.buildQuery(params)
    Logger.logger.debug(queryRequest.queryString())

    WS.url(SolrHelper.buildSearchUrl(queryRequest)).get.map { response =>
      checkError(response).right.map { r =>
        val resp = new QueryResponse(writerType=queryRequest.writerType, rawBody=r.body)
        val facetClasses = SolrHelper.extract(resp, params.entity, params.facets)
        val nodes = xml.XML.loadString(r.body)

        ItemPage(itemsFromXml(nodes), params.page, offset, params.limit, resp.response.numFound, facetClasses)
      }
    }
  }

  def facet(
    facet: String,
    sort: String = "name",
    params: SearchParams): Future[Either[RestError,solr.FacetPage[solr.facet.Facet]]] = {
    val offset = (params.page - 1) * params.limit

    // create a response returning 0 documents - we don't
    // actually care about the documents, so even this is
    // not strictly necessary... we also don't care about the
    // ordering.
    val queryRequest = SolrHelper.buildQuery(params)

    WS.url(SolrHelper.buildSearchUrl(queryRequest)).get.map { response =>
      checkError(response).right.map { r =>
        val resp = new QueryResponse(writerType=queryRequest.writerType, rawBody=r.body)
        val facetClasses = SolrHelper.extract(resp, params.entity, params.facets)

        val facetClass = facetClasses.find(_.param==facet).getOrElse(
            throw new Exception("Unknown facet: " + facet))
        val facets = sort match {
          case "name" => facetClass.sortedByName.slice(offset, offset + params.limit)
          case _ => facetClass.sortedByCount.slice(offset, offset + params.limit)
        }
        FacetPage(facetClass, facets, params.page, offset, params.limit, facetClass.count)
      }
    }
  }
}
