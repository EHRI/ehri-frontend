import auth.AccountManager
import auth.oauth2.{OAuth2Flow, WebOAuth2Flow}
import backend._
import backend.aws.S3FileStorage
import backend.googledocs.GoogleDocsHtmlPages
import backend.helpdesk.EhriHelpdesk
import backend.parse.ParseFeedbackDAO
import backend.rest.cypher.{Cypher, CypherDAO}
import backend.rest.{CypherIdGenerator, GidSearchResolver, SearchDAO}
import com.google.inject.AbstractModule
import eu.ehri.project.search.solr.{JsonResponseHandler, ResponseHandler, SolrSearchEngine}
import global.{AppGlobalConfig, GlobalBackend, GlobalConfig, GlobalEventHandler}
import indexing.CmdlineIndexer
import models.{DatabaseGuideDAO, GuideDAO}
import utils.search.{SearchEngine, SearchIndexer, SearchItemResolver}
import utils.{DbMovedPageLookup, MovedPageLookup}
import views.{MarkdownRenderer, PegdownMarkdownRendererProvider}


class AppModule extends AbstractModule {
  protected def configure(): Unit = {
    bind(classOf[AccountManager]).to(classOf[auth.sql.SqlAccountManager])
    bind(classOf[GlobalConfig]).to(classOf[AppGlobalConfig])
    bind(classOf[ResponseHandler]).to(classOf[JsonResponseHandler])
    bind(classOf[SearchIndexer]).to(classOf[CmdlineIndexer])
    bind(classOf[SearchEngine]).to(classOf[SolrSearchEngine])
    bind(classOf[SearchItemResolver]).to(classOf[GidSearchResolver])
    bind(classOf[EventHandler]).to(classOf[GlobalEventHandler])
    bind(classOf[Backend]).to(classOf[GlobalBackend])
    bind(classOf[SearchDAO]).to(classOf[backend.rest.Search])
    bind(classOf[FeedbackDAO]).to(classOf[ParseFeedbackDAO])
    bind(classOf[HelpdeskDAO]).to(classOf[EhriHelpdesk])
    bind(classOf[IdGenerator]).to(classOf[CypherIdGenerator])
    bind(classOf[OAuth2Flow]).to(classOf[WebOAuth2Flow])
    bind(classOf[MovedPageLookup]).to(classOf[DbMovedPageLookup])
    bind(classOf[FileStorage]).to(classOf[S3FileStorage])
    bind(classOf[HtmlPages]).to(classOf[GoogleDocsHtmlPages])
    bind(classOf[GuideDAO]).to(classOf[DatabaseGuideDAO])
    bind(classOf[MarkdownRenderer]).toProvider(classOf[PegdownMarkdownRendererProvider])
    bind(classOf[Cypher]).to(classOf[CypherDAO])
  }
}