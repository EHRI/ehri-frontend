package controllers

import javax.inject.Inject

import akka.stream.Materializer
import auth.handler.AuthHandler
import com.google.inject.ImplementedBy
import global.GlobalConfig
import play.api.Configuration
import play.api.cache.{Cached, SyncCacheApi}
import services.accounts.AccountManager
import services.data.DataApi
import services.redirects.MovedPageLookup
import services.search.{SearchEngine, SearchItemResolver}
import views.MarkdownRenderer


/**
  * Common components required by portal controllers.
  */
@ImplementedBy(classOf[DefaultAppComponents])
trait AppComponents {
  def materializer: Materializer
  def dataApi: DataApi
  def accounts: AccountManager
  def authHandler: AuthHandler
  def cacheApi: SyncCacheApi
  def statusCache: Cached
  def config: Configuration
  def globalConfig: GlobalConfig
  def markdown: MarkdownRenderer
  def pageRelocator: MovedPageLookup
  def searchEngine: SearchEngine
  def searchResolver: SearchItemResolver
}

case class DefaultAppComponents @Inject ()(
  accounts: AccountManager,
  authHandler: AuthHandler,
  cacheApi: SyncCacheApi,
  config: Configuration,
  dataApi: DataApi,
  globalConfig: GlobalConfig,
  markdown: MarkdownRenderer,
  materializer: Materializer,
  pageRelocator: MovedPageLookup,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  statusCache: Cached
) extends AppComponents

