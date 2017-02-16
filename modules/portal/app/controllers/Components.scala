package controllers

import javax.inject.Inject

import akka.stream.Materializer
import auth.AccountManager
import auth.handler.AuthHandler
import backend.DataApi
import global.GlobalConfig
import play.api.Configuration
import play.api.cache.{CacheApi, Cached}
import play.api.http.FileMimeTypes
import play.api.i18n.{Langs, MessagesApi}
import play.api.mvc._
import utils.MovedPageLookup
import utils.search.{SearchEngine, SearchItemResolver}
import views.MarkdownRenderer

import scala.concurrent.ExecutionContext

/**
  * Common components required by portal controllers.
  */
case class Components @Inject ()(
  accounts: AccountManager,
  actionBuilder: DefaultActionBuilder,
  authHandler: AuthHandler,
  cacheApi: CacheApi,
  configuration: Configuration,
  dataApi: DataApi,
  executionContext: ExecutionContext,
  fileMimeTypes: FileMimeTypes,
  globalConfig: GlobalConfig,
  langs: Langs,
  markdown: MarkdownRenderer,
  materializer: Materializer,
  messagesApi: MessagesApi,
  pageRelocator: MovedPageLookup,
  parsers: PlayBodyParsers,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  statusCache: Cached
) extends ControllerComponents

