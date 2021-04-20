package controllers

import akka.stream.Materializer
import auth.handler.AuthHandler
import com.google.inject.ImplementedBy
import config.AppConfig
import lifecycle.ItemLifecycle
import play.api.Configuration
import play.api.cache.SyncCacheApi
import services.accounts.AccountManager
import services.data.DataServiceBuilder
import services.redirects.MovedPageLookup
import services.search.{SearchEngine, SearchItemResolver}
import views.html.MarkdownRenderer

import javax.inject.Inject


/**
  * Common components required by portal controllers.
  */
@ImplementedBy(classOf[DefaultAppComponents])
trait AppComponents {
  def materializer: Materializer
  def dataApi: DataServiceBuilder
  def accounts: AccountManager
  def authHandler: AuthHandler
  def cacheApi: SyncCacheApi
  def config: Configuration
  def conf: AppConfig
  def markdown: MarkdownRenderer
  def pageRelocator: MovedPageLookup
  def searchEngine: SearchEngine
  def searchResolver: SearchItemResolver
  def itemLifecycle: ItemLifecycle
}

case class DefaultAppComponents @Inject()(
  accounts: AccountManager,
  authHandler: AuthHandler,
  cacheApi: SyncCacheApi,
  config: Configuration,
  dataApi: DataServiceBuilder,
  conf: AppConfig,
  markdown: MarkdownRenderer,
  materializer: Materializer,
  pageRelocator: MovedPageLookup,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  itemLifecycle: ItemLifecycle,
) extends AppComponents

