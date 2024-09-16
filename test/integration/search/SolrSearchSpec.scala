package integration.search

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.util.ByteString
import eu.ehri.project.search.solr.SolrSearchEngine
import org.specs2.specification.BeforeAfterAll
import play.api.test.{PlaySpecification, WithApplication}
import play.api.{Application, Environment}
import services.ServiceConfig
import services.search.{SearchHit, SearchParams, SearchQuery, SearchResult}

import java.nio.file.Paths
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future


class SolrSearchSpec extends PlaySpecification with BeforeAfterAll {

  private implicit val system: ActorSystem = ActorSystem.create("search-test")
  private implicit val mat: Materializer = Materializer.matFromSystem

  "SolrSearch" should {
    "response 'ok' to status" in new WithApplication {
      val sc = app.injector.instanceOf[SolrSearchEngine]
      await(sc.status()) must_== "ok"
    }

    "return an appropriate number of items on glob select" in new WithApplication {
      val r = await(search("*"))
      r.page.total must beGreaterThan(4000)
    }
  }

  private def search(q: String)(implicit app: Application): Future[SearchResult[SearchHit]] = {
    val params = SearchParams(query = Some(q))
    val query = SearchQuery(params = params)
    val sc = app.injector.instanceOf[SolrSearchEngine]
    sc.search(query)
  }

  private def solrUpdate(entity: UniversalEntity): Future[String] = {
    val url = ServiceConfig("solr", play.api.Configuration.load(Environment.simple())).baseUrl + "/update?commit=true"
    val req = HttpRequest(HttpMethods.POST, url).withEntity(entity)
    Http().singleRequest(req).flatMap { r =>
      val out = r.entity.withoutSizeLimit().dataBytes.runFold(ByteString.empty)(_ ++ _)
      out.map(_.utf8String)
    }
  }

  override def beforeAll(): Unit = {
    val path = Paths.get(getClass.getClassLoader.getResource("searchdata.json").toURI)
    val entity = HttpEntity.fromPath(ContentTypes.`application/json`, path)
    await(solrUpdate(entity))
  }

  override def afterAll(): Unit = {
    val deleteQuery = """{"delete": {"query": "*:*"}}"""
    val entity = HttpEntity(ContentTypes.`application/json`, deleteQuery)
    await(solrUpdate(entity))
  }
}
