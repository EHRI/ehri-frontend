package controllers.core

import controllers.base.EntitySearch
import play.api.mvc._
import play.api.libs.json.Json
import utils.search.Dispatcher
import com.google.inject._

@Singleton
class SearchFilter @Inject()(val globalConfig: global.GlobalConfig) extends EntitySearch {

  val searchEntities = List() // i.e. Everything

  /**
   * Quick filter action that searches applies a 'q' string filter to
   * only the name_ngram field and returns an id/name pair.
   * @return
   */
  def filter = filterAction() { page => implicit userOpt => implicit request =>
    Ok(Json.obj(
      "numPages" -> page.numPages,
      "page" -> page.page,
      "items" -> page.items.map { case (id, name, t) =>
        Json.arr(id, name, t.toString)
      }
    ))
  }
}
