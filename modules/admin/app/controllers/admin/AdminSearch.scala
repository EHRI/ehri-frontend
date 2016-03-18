package controllers.admin

import javax.inject._

import controllers.Components
import controllers.base.AdminController
import controllers.generic.Search
import defines.EntityType
import models.base.{AnyModel, Description}
import play.api.i18n.Messages
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent}
import utils.search._
import views.Helpers


@Singleton
case class AdminSearch @Inject()(
  components: Components,
  searchIndexer: SearchIndexMediator
) extends AdminController with Search {

  // i.e. Everything

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
        name = Messages("search.type"),
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
    EntityType.UserProfile,
    EntityType.Group,
    EntityType.VirtualUnit,
    EntityType.Link
  )

  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads

  def search: Action[AnyContent] = OptionalUserAction.async { implicit request =>
    find[AnyModel](
      defaultParams = SearchParams(sort = Some(SearchOrder.Score)),
      entities = searchTypes.toList,
      facetBuilder = entityFacets
    ).map { result =>
      render {
        case Accepts.Json() =>
          Ok(Json.toJson(Json.obj(
            "numPages" -> result.page.numPages,
            "page" -> Json.toJson(result.page.items.map(_._1))(Writes.seq(client.json.anyModelJson.clientFormat)),
            "facets" -> result.facetClasses
          )))
        case _ => Ok(views.html.admin.search.search(
          result,
          controllers.admin.routes.AdminSearch.search())
        )
      }
    }
  }
}
