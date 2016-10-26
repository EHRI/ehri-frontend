package controllers

import javax.inject.Inject

import akka.stream.Materializer
import auth.AccountManager
import auth.handler.AuthHandler
import backend.DataApi
import global.GlobalConfig
import play.api.Configuration
import play.api.cache.{CacheApi, Cached}
import play.api.i18n.MessagesApi
import utils.MovedPageLookup
import utils.search.{SearchEngine, SearchItemResolver}
import views.MarkdownRenderer

import scala.concurrent.ExecutionContext

/**
  * Common components required by portal controllers.
  */
case class Components @Inject ()(
  configuration: Configuration,
  cacheApi: CacheApi,
  statusCache: Cached,
  executionContext: ExecutionContext,
  globalConfig: GlobalConfig,
  messagesApi: MessagesApi,
  pageRelocator: MovedPageLookup,
  markdown: MarkdownRenderer,
  accounts: AccountManager,
  dataApi: DataApi,
  authHandler: AuthHandler,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  materializer: Materializer
)

