package controllers.admin

import auth.AccountManager
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import utils.MovedPageLookup
import utils.search.{SearchEngine, SearchItemResolver}
import javax.inject._

import auth.handler.AuthHandler
import controllers.generic.Search
import backend.DataApi
import controllers.base.AdminController

import scala.concurrent.ExecutionContext


@Singleton
case class SearchFilter @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  authHandler: AuthHandler,
  executionContext: ExecutionContext,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  dataApi: DataApi,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi
) extends AdminController
  with Search {

  override val staffOnly = false
  override val verifiedOnly = false

  /**
   * Quick filter action that searches applies a 'q' string filter to
   * only the name_ngram field and returns an id/name pair.
   */
  def filterItems = OptionalUserAction.async { implicit request =>
    filter().map { page =>
      Ok(Json.obj(
        "numPages" -> page.numPages,
        "page" -> page.page,
        "items" -> page.items
      ))
    }
  }
}
