package controllers.portal

import controllers.base.EntitySearch
import models.base.AnyModel
import play.api._
import play.api.mvc._
import views.html._
import play.api.http.MimeTypes

import com.google.inject._
import utils.search.{SearchOrder, SearchParams}
import play.api.libs.json.{Writes, Json}


@Singleton
class Application @Inject()(implicit val globalConfig: global.GlobalConfig) extends Controller with EntitySearch {

  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads

  def search = searchAction[AnyModel](defaultParams = Some(SearchParams(sort = Some(SearchOrder.Score)))) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(Json.toJson(Json.obj(
      "numPages" -> page.numPages,
      "page" -> Json.toJson(page.items.map(_._1))(Writes.seq(AnyModel.Converter.clientFormat)),
      "facets" -> facets
    )))
  }

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




  def routes = Action { implicit request =>

    import controllers.core.routes.javascript._
    import controllers.archdesc.routes.javascript.DocumentaryUnits
    import controllers.archdesc.routes.javascript.Countries
    import controllers.archdesc.routes.javascript.Repositories
    import controllers.vocabs.routes.javascript.Vocabularies
    import controllers.vocabs.routes.javascript.Concepts
    import controllers.authorities.routes.javascript.AuthoritativeSets
    import controllers.authorities.routes.javascript.HistoricalAgents

    Ok(
      Routes.javascriptRouter("jsRoutes")(
        controllers.portal.routes.javascript.Application.search,
        controllers.portal.routes.javascript.Application.filter,
        Application.getType,
        UserProfiles.list,
        UserProfiles.get,
        Groups.list,
        Groups.get,
        DocumentaryUnits.search,
        DocumentaryUnits.get,
        Countries.search,
        Countries.get,
        Repositories.search,
        Repositories.get,
        Vocabularies.list,
        Vocabularies.get,
        Concepts.search,
        Concepts.get,
        AuthoritativeSets.list,
        AuthoritativeSets.get,
        HistoricalAgents.search,
        HistoricalAgents.get
      )
    ).as(MimeTypes.JAVASCRIPT)
  }

  def index = Action { implicit request =>
    Ok("portal")
  }
}

