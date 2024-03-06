package integration.search

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.util.ByteString
import config.ServiceConfig
import helpers.SearchTestRunner
import play.api.{Application, Configuration, Environment, Logger}
import services.search._
import utils.PageParams

import java.nio.file.Paths
import scala.concurrent.{ExecutionContext, Future}


/**
  * Spec to test the ingest UI and websocket monitoring.
  */
class SolrSearchSpec extends SearchTestRunner {

  val logger = Logger(classOf[SolrSearchSpec])

  private def initSolr(): Unit = {
    val env = Environment.simple()
    val config = Configuration.load(env)
    val port = config.get[Int]("services.solr.port")
    if (port == 8983) {
      throw new RuntimeException(s"Solr port is set to default value: $port, bailing out...")
    }

    implicit val as: ActorSystem = ActorSystem()
    val mat = Materializer(as)
    implicit val ec: ExecutionContext = mat.executionContext

    def req(payload: UniversalEntity): Future[HttpResponse] = {
      val url = ServiceConfig("solr", config).baseUrl + "/update?commit=true"
      Http().singleRequest(HttpRequest(HttpMethods.POST, url).withEntity(payload))
    }

    logger.debug("Clearing Solr data...")
    val json = ByteString.fromString("""{"delete": {"query": "*:*"}}""")
    await(req(HttpEntity.apply(ContentTypes.`application/json`, json)))

    logger.debug("Loading Solr data...")
    val resource = Paths.get(getClass.getResource("/searchdata.json").toURI)
    val entity = HttpEntity.fromPath(ContentTypes.`application/json`, resource)
    await(req(entity))

    await(as.terminate())
  }
  initSolr()


  def engine(implicit app: Application) = app.injector.instanceOf[SearchEngine]

  def simpleSearch(engine: SearchEngine, q: String): Future[SearchResult[SearchHit]] =
    engine.search(SearchQuery(
      params = SearchParams(query = Some(q)),
      paging = PageParams.empty.withoutLimit))

  "Solr search engine should" should {
    "find things" in new ITestApp {
      val r = await(simpleSearch(engine, "USHMM"))
      r.page.size must be_>(0)
    }

    "find other things" in new ITestApp {
      val r = await(simpleSearch(engine, "Wiener Library"))
      r.page.size must be_>(0)
    }
  }
}
