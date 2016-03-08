import javax.inject.{Inject, Provider}

import auth.AccountManager
import auth.oauth2.{OAuth2Flow, WebOAuth2Flow}
import backend._
import backend.aws.S3FileStorage
import backend.googledocs.GoogleDocsHtmlPages
import backend.helpdesk.EhriHelpdesk
import backend.parse.{ParseCypherQueryDAO, ParseFeedbackDAO}
import backend.rest.cypher.{Cypher, CypherDAO}
import backend.rest.{RestApi, CypherIdGenerator, GidSearchResolver}
import com.google.inject.AbstractModule
import eu.ehri.project.indexing.index.Index
import eu.ehri.project.indexing.index.impl.SolrIndex
import eu.ehri.project.search.solr.{JsonResponseHandler, ResponseHandler, SolrSearchEngine}
import global.{AppGlobalConfig, GlobalConfig, GlobalEventHandler}
import indexing.SearchToolsIndexMediator
import models.{DatabaseGuideDAO, GuideDAO}
import utils.search.{SearchEngine, SearchIndexMediator, SearchItemResolver}
import utils.{DbMovedPageLookup, MovedPageLookup}
import views.{MarkdownRenderer, PegDownMarkdownRendererProvider}

private class SolrIndexProvider @Inject()(config: play.api.Configuration) extends Provider[Index] {
  override def get(): Index = new SolrIndex(utils.serviceBaseUrl("solr", config))
}

class AppModule extends AbstractModule {
  protected def configure(): Unit = {
    bind(classOf[AccountManager]).to(classOf[auth.sql.SqlAccountManager])
    bind(classOf[GlobalConfig]).to(classOf[AppGlobalConfig])
    bind(classOf[Index]).toProvider(classOf[SolrIndexProvider])
    bind(classOf[ResponseHandler]).to(classOf[JsonResponseHandler])
    bind(classOf[SearchIndexMediator]).to(classOf[SearchToolsIndexMediator])
    bind(classOf[SearchEngine]).to(classOf[SolrSearchEngine])
    bind(classOf[SearchItemResolver]).to(classOf[GidSearchResolver])
    bind(classOf[EventHandler]).to(classOf[GlobalEventHandler])
    bind(classOf[DataApi]).to(classOf[RestApi])
    bind(classOf[FeedbackDAO]).to(classOf[ParseFeedbackDAO])
    bind(classOf[CypherQueryDAO]).to(classOf[ParseCypherQueryDAO])
    bind(classOf[HelpdeskDAO]).to(classOf[EhriHelpdesk])
    bind(classOf[IdGenerator]).to(classOf[CypherIdGenerator])
    bind(classOf[OAuth2Flow]).to(classOf[WebOAuth2Flow])
    bind(classOf[MovedPageLookup]).to(classOf[DbMovedPageLookup])
    bind(classOf[FileStorage]).to(classOf[S3FileStorage])
    bind(classOf[HtmlPages]).to(classOf[GoogleDocsHtmlPages])
    bind(classOf[GuideDAO]).to(classOf[DatabaseGuideDAO])
    bind(classOf[MarkdownRenderer]).toProvider(classOf[PegDownMarkdownRendererProvider])
    bind(classOf[Cypher]).to(classOf[CypherDAO])
  }
}