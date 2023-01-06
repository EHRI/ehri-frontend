package guice

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import config.AppConfig
import data.markdown.{CommonmarkMarkdownRenderer, RawMarkdownRenderer, SanitisingMarkdownRenderer}
import lifecycle.{GeocodingItemLifecycle, ItemLifecycle}
import play.api.libs.concurrent.AkkaGuiceSupport
import services.RateLimitChecker
import services.cypher.{CypherQueryService, CypherService, SqlCypherQueryService, WsCypherService}
import services.data._
import services.feedback.{FeedbackService, SqlFeedbackService}
import services.htmlpages.{GoogleDocsHtmlPages, HtmlPages}
import services.redirects.{MovedPageLookup, SqlMovedPageLookup}
import services.search._
import services.storage.{FileStorage, S3CompatibleFileStorage}
import views.html.MarkdownRenderer

import javax.inject.{Inject, Provider}
import scala.concurrent.ExecutionContext

private class PortalStorageProvider @Inject()(config: play.api.Configuration)(implicit as: ActorSystem, mat: Materializer, ec: ExecutionContext) extends Provider[FileStorage] {
  override def get(): FileStorage =
    S3CompatibleFileStorage(config.get[com.typesafe.config.Config]("storage.portal"))
}

private class DamStorageProvider @Inject()(config: play.api.Configuration)(implicit as: ActorSystem, mat: Materializer, ec: ExecutionContext) extends Provider[FileStorage] {
  override def get(): FileStorage =
    S3CompatibleFileStorage(config.get[com.typesafe.config.Config]("storage.dam"))
}

class AppModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bind(classOf[AppConfig])
    bind(classOf[RateLimitChecker])
    bind(classOf[EventHandler]).to(classOf[IndexingEventHandler])
    bind(classOf[ItemLifecycle]).to(classOf[GeocodingItemLifecycle])
    bind(classOf[DataServiceBuilder]).to(classOf[WsDataServiceBuilder])
    bind(classOf[FeedbackService]).to(classOf[SqlFeedbackService])
    bind(classOf[CypherQueryService]).to(classOf[SqlCypherQueryService])
    bind(classOf[IdGenerator]).to(classOf[CypherIdGenerator])
    bind(classOf[MovedPageLookup]).to(classOf[SqlMovedPageLookup])
    bind(classOf[FileStorage]).toProvider(classOf[PortalStorageProvider])
    bind(classOf[FileStorage]).annotatedWith(Names.named("dam")).toProvider(classOf[DamStorageProvider])
    bind(classOf[HtmlPages]).to(classOf[GoogleDocsHtmlPages])
    bind(classOf[RawMarkdownRenderer]).to(classOf[CommonmarkMarkdownRenderer])
    bind(classOf[MarkdownRenderer]).to(classOf[SanitisingMarkdownRenderer])
    bind(classOf[CypherService]).to(classOf[WsCypherService])
    bindActor[EventForwarder]("event-forwarder")
  }
}
