package controllers.admin

import javax.inject._

import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic.Search
import defines.EntityType
import models.base.{Model, Description}
import play.api.i18n.Messages
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import utils.PageParams
import services.search._
import views.Helpers


@Singleton
case class AdminSearch @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  searchIndexer: SearchIndexMediator
) extends AdminController with Search {
  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = Description.LANG_CODE,
        name = Messages("documentaryUnit." + Description.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = "type",
        name = Messages("facet.type"),
        param = "type",
        render = s => Messages("contentTypes." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key = "copyrightStatus",
        name = Messages("copyrightStatus.copyright"),
        param = "copyright",
        render = s => Messages("copyrightStatus." + s)
      ),
      FieldFacetClass(
        key = "scope",
        name = Messages("scope.scope"),
        param = "scope",
        render = s => Messages("scope." + s)
      )
    )
  }

  private val searchTypes = Seq(
    EntityType.Country,
    EntityType.DocumentaryUnit,
    EntityType.HistoricalAgent,
    EntityType.Repository,
    EntityType.Concept,
    EntityType.Vocabulary,
    EntityType.AuthoritativeSet,
    EntityType.VirtualUnit,
  )

  def search(params: SearchParams, paging: PageParams): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    find[Model](params, paging, entities = searchTypes, facetBuilder = entityFacets).map { result =>
      render {
        case Accepts.Json() => Ok(Json.obj(
            "numPages" -> result.page.numPages,
            "page" -> Json.toJson(result.page.items.map(_._1))(Writes.seq(client.json.anyModelJson.clientFormat)),
            "facets" -> result.facetClasses
          ))
        case _ => Ok(views.html.admin.search.search(result, controllers.admin.routes.AdminSearch.search()))
      }
    }
  }
}
