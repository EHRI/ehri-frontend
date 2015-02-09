package controllers.admin

import auth.AccountManager
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import utils.search.{SearchItemResolver, SearchEngine}
import com.google.inject._
import controllers.generic.Search
import backend.Backend
import controllers.base.AdminController

@Singleton
case class SearchFilter @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver, backend: Backend, accounts: AccountManager)
  extends AdminController
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
