package controllers.admin

import javax.inject._

import controllers.Components
import controllers.base.AdminController
import controllers.generic.Search
import play.api.libs.json.Json


@Singleton
case class SearchFilter @Inject()(components: Components) extends AdminController with Search {

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
