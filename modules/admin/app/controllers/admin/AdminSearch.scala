package controllers.admin

import auth.AccountManager
import backend.rest.cypher.Cypher
import play.api.cache.CacheApi
import play.api.libs.concurrent.Execution.Implicits._
import utils.MovedPageLookup
import play.api.i18n.{MessagesApi, Messages}
import views.{MarkdownRenderer, Helpers}
import play.api.libs.json.{Writes, Json}

import javax.inject._
import models.base.{Description, AnyModel}
import utils.search._
import controllers.generic.Search
import backend.DataApi
import defines.EntityType
import controllers.base.AdminController


@Singleton
case class AdminSearch @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  searchIndexer: SearchIndexMediator,
  dataApi: DataApi,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher
) extends AdminController
  with Search {

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
    EntityType.VirtualUnit
  )

  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads

  def search = OptionalUserAction.async { implicit request =>
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
          ))
          )
        case _ => Ok(views.html.admin.search.search(
          result,
          controllers.admin.routes.AdminSearch.search())
        )
      }
    }
  }
}
