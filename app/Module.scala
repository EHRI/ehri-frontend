import akka.stream.Materializer
import auth.handler.AuthIdContainer
import auth.handler.cookie.CookieIdContainer
import auth.oauth2.OAuth2Config
import auth.oauth2.providers._
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import eu.ehri.project.indexing.index.Index
import eu.ehri.project.indexing.index.impl.SolrIndex
import eu.ehri.project.search.solr._
import global.{AppGlobalConfig, GlobalConfig, GlobalEventHandler, _}
import javax.inject.{Inject, Provider}
import models.{GuideService, SqlGuideService}
import services.accounts.{AccountManager, SqlAccountManager}
import services.cypher.{CypherQueryService, CypherService, Neo4jCypherService, SqlCypherQueryService}
import services.data.{GidSearchResolver, _}
import services.feedback.{FeedbackService, SqlFeedbackService}
import services.harvesting.{HarvestEventService, OaiPmhClient, WSOaiPmhClient, SqlHarvestEventService}
import services.htmlpages.{GoogleDocsHtmlPages, HtmlPages}
import services.ingest.{EadValidator, RelaxNGEadValidator, IngestService, WSIngestService}
import services.oauth2.{OAuth2Service, WebOAuth2Service}
import services.redirects.{MovedPageLookup, SqlMovedPageLookup}
import services.search.{AkkaStreamsIndexMediator, SearchEngine, SearchIndexMediator, SearchItemResolver}
import services.storage.{FileStorage, S3CompatibleFileStorage}
import utils.markdown.{CommonmarkMarkdownRenderer, RawMarkdownRenderer, SanitisingMarkdownRenderer}
import views.MarkdownRenderer

private class SolrIndexProvider @Inject()(config: play.api.Configuration) extends Provider[Index] {
  override def get(): Index = new SolrIndex(utils.serviceBaseUrl("solr", config))
}

private class OAuth2ConfigProvider @Inject()(config: play.api.Configuration) extends Provider[OAuth2Config] {
  override def get(): OAuth2Config = new OAuth2Config {
    override def providers: Seq[OAuth2Provider] = Seq(
      GoogleOAuth2Provider(config),
      MicrosoftOAuth2Provider(config),
      FacebookOAuth2Provider(config),
      YahooOAuth2Provider(config)
    )
  }
}

private class AWSStorageProvider @Inject()(config: play.api.Configuration)(implicit mat: Materializer) extends Provider[FileStorage] {
  override def get(): FileStorage =
    S3CompatibleFileStorage(config.get[com.typesafe.config.Config]("alpakka.s3.aws"))
}

private class DAMStorageProvider @Inject()(config: play.api.Configuration)(implicit mat: Materializer) extends Provider[FileStorage] {
  override def get(): FileStorage =
    S3CompatibleFileStorage(config.get[com.typesafe.config.Config]("digitalocean.spaces"))
}

class Module extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[AuthIdContainer]).to(classOf[CookieIdContainer])
    bind(classOf[AccountManager]).to(classOf[SqlAccountManager])
    bind(classOf[GlobalConfig]).to(classOf[AppGlobalConfig])
    bind(classOf[Index]).toProvider(classOf[SolrIndexProvider])
    bind(classOf[ResponseParser]).to(classOf[SolrJsonResponseParser])
    bind(classOf[QueryBuilder]).to(classOf[SolrQueryBuilder])
    bind(classOf[SearchIndexMediator]).to(classOf[AkkaStreamsIndexMediator])
    bind(classOf[SearchEngine]).to(classOf[SolrSearchEngine])
    bind(classOf[SearchItemResolver]).to(classOf[GidSearchResolver])
    bind(classOf[EventHandler]).to(classOf[GlobalEventHandler])
    bind(classOf[ItemLifecycle]).to(classOf[GeocodingItemLifecycle])
    bind(classOf[DataApi]).to(classOf[DataApiService])
    bind(classOf[FeedbackService]).to(classOf[SqlFeedbackService])
    bind(classOf[CypherQueryService]).to(classOf[SqlCypherQueryService])
    bind(classOf[IdGenerator]).to(classOf[CypherIdGenerator])
    bind(classOf[OAuth2Service]).to(classOf[WebOAuth2Service])
    bind(classOf[OAuth2Config]).toProvider(classOf[OAuth2ConfigProvider])
    bind(classOf[MovedPageLookup]).to(classOf[SqlMovedPageLookup])
    bind(classOf[FileStorage]).toProvider(classOf[AWSStorageProvider])
    bind(classOf[FileStorage]).annotatedWith(Names.named("dam")).toProvider(classOf[DAMStorageProvider])
    bind(classOf[HtmlPages]).to(classOf[GoogleDocsHtmlPages])
    bind(classOf[GuideService]).to(classOf[SqlGuideService])
    bind(classOf[RawMarkdownRenderer]).to(classOf[CommonmarkMarkdownRenderer])
    bind(classOf[MarkdownRenderer]).to(classOf[SanitisingMarkdownRenderer])
    bind(classOf[CypherService]).to(classOf[Neo4jCypherService])
    bind(classOf[IngestService]).to(classOf[WSIngestService])
    bind(classOf[EadValidator]).to(classOf[RelaxNGEadValidator])
    bind(classOf[OaiPmhClient]).to(classOf[WSOaiPmhClient])
    bind(classOf[HarvestEventService]).to(classOf[SqlHarvestEventService])
  }
}
