package modules

import auth.AccountManager
import auth.oauth2.{WebOAuth2Flow, OAuth2Flow}
import backend._
import backend.aws.S3FileStorage
import backend.googledocs.GoogleDocsHtmlPages
import backend.helpdesk.EhriHelpdesk
import backend.parse.ParseFeedbackDAO
import backend.rest.cypher.{CypherDAO, Cypher}
import backend.rest.{SearchDAO, CypherIdGenerator, GidSearchResolver}
import com.google.inject.AbstractModule
import com.typesafe.plugin.{MockMailer, MailerAPI}
import eu.ehri.project.search.solr.{SolrSearchEngine, JsonResponseHandler, ResponseHandler}
import global.{AppGlobalConfig, GlobalBackend, GlobalEventHandler, GlobalConfig}
import indexing.CmdlineIndexer
import models.{DatabaseGuideDAO, GuideDAO}
import utils.{DbMovedPageLookup, MovedPageLookup}
import utils.search.{SearchItemResolver, SearchEngine, SearchIndexer}
import views.{PegdownMarkdownRendererProvider, MarkdownRenderer}


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
    bind(classOf[MailerAPI]).toInstance(MockMailer)
    bind(classOf[OAuth2Flow]).to(classOf[WebOAuth2Flow])
    bind(classOf[MovedPageLookup]).to(classOf[DbMovedPageLookup])
    bind(classOf[FileStorage]).to(classOf[S3FileStorage])
    bind(classOf[HtmlPages]).to(classOf[GoogleDocsHtmlPages])
    bind(classOf[GuideDAO]).to(classOf[DatabaseGuideDAO])
    bind(classOf[MarkdownRenderer]).toProvider(classOf[PegdownMarkdownRendererProvider])
    bind(classOf[Cypher]).to(classOf[CypherDAO])
  }
}