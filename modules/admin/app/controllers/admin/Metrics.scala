package controllers.admin

import javax.inject._
import client.json.ClientWriteable
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic.Search
import models.{EntityType, Isaar}
import models.base.{Description, Model}
import play.api.cache.{Cached, SyncCacheApi}
import play.api.i18n.Messages
import play.api.libs.json.{Json, Writes, __}
import play.api.mvc._
import services.search._
import utils.{Page, PageParams}
import views.Helpers


@Singleton
case class Metrics @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  cache: SyncCacheApi,
  statusCache: Cached,
) extends AdminController with Search {
  import scala.concurrent.duration._

  private val metricCacheTime = 1.hour

  import play.api.libs.functional.syntax._
  private implicit def pageWrites[T](implicit r: Writes[T]): Writes[Page[T]] = (
    (__ \ "offset").write[Int] and
      (__ \ "limit").write[Int] and
      (__ \ "total").write[Int] and
      (__ \ "values").lazyWrite(Writes.seq[T](r))
    )(unlift(Page.unapply[T]))


  private def jsonResponse[T](result: SearchResult[(T, SearchHit)])(implicit request: Request[AnyContent], w: ClientWriteable[T]): Result = {
    render {
      case Accepts.Json() | Accepts.JavaScript() => Ok(Json.obj(
        "page" -> Json.toJson(result.mapItems(_._1).page)(pageWrites(w.clientFormat)),
        "params" -> result.params,
        "appliedFacets" -> result.facets,
        "facetClasses" -> result.facetClasses
      )).as(play.api.http.ContentTypes.JSON)
      case _ => UnsupportedMediaType
    }
  }

  // For all of the metrics we're just using facet counts,
  // so set the result limit to be zero.
  private val langCountFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key=Description.LANG_CODE,
        name=Messages("documentaryUnit." + Description.LANG_CODE),
        param="lang",
        render= (s: String) => Helpers.languageCodeToName(s)
      )
    )
  }

  def languageOfMaterial: EssentialAction = statusCache.status((_: RequestHeader) => "pages:langMetric", OK, metricCacheTime) {
    OptionalUserAction.async { implicit request =>
      find[Model](SearchParams.empty, PageParams(limit = 0),
        entities = List(EntityType.DocumentaryUnit), facetBuilder = langCountFacets)
        .map(jsonResponse[Model])
    }
  }


  private val holdingRepoFacets: FacetBuilder = { implicit request =>
    List(
      // Holding repository
      FieldFacetClass(
        key="repositoryName",
        name=Messages("documentaryUnit.heldBy"),
        param="holder"
      )
    )
  }

  def holdingRepository: EssentialAction = statusCache.status((_: RequestHeader) => "pages:repoMetric", OK, metricCacheTime) {
    OptionalUserAction.async { implicit request =>
      find[Model](SearchParams.empty, PageParams(limit = 0),
        entities = List(EntityType.DocumentaryUnit), facetBuilder = holdingRepoFacets)
        .map(jsonResponse[Model])
    }
  }

  private val countryRepoFacets: FacetBuilder = { implicit request =>
    List(

      // Repositories by country
      FieldFacetClass(
        key="countryCode",
        name=Messages("repository.countryCode"),
        param="country",
        render=Helpers.countryCodeToName
      )
    )
  }

  def repositoryCountries: EssentialAction = statusCache.status((_: RequestHeader) => "pages:repoCountryMetric", OK, metricCacheTime) {
    OptionalUserAction.async { implicit request =>
      find[Model](SearchParams.empty, PageParams(limit = 0),
        entities = List(EntityType.Repository), facetBuilder = countryRepoFacets)
        .map(jsonResponse[Model])
    }
  }

  private val restrictedFacets: FacetBuilder = { implicit request =>
    List(
      // Historical agent type
      FieldFacetClass(
        key=services.search.SearchConstants.RESTRICTED_FIELD,
        name=Messages("search.isRestricted"),
        param="restricted",
        render=s => Messages("restricted" + "." + s)
      )
    )
  }

  def restricted: EssentialAction = statusCache.status((_: RequestHeader) => "pages:restrictedMetric", OK, metricCacheTime) {
    OptionalUserAction.async { implicit request =>
      find[Model](SearchParams.empty, PageParams(limit = 0),
        entities = List(EntityType.HistoricalAgent, EntityType.DocumentaryUnit, EntityType.HistoricalAgent),
        facetBuilder = restrictedFacets)
        .map(jsonResponse[Model])
    }
  }


  private val agentTypeFacets: FacetBuilder = { implicit request =>
    List(
      // Historical agent type
      FieldFacetClass(
        key=models.Isaar.ENTITY_TYPE,
        name=Messages("historicalAgent." + Isaar.ENTITY_TYPE),
        param="cpf",
        render=s => Messages("historicalAgent." + s)
      )
    )
  }

  def agentTypes: EssentialAction = statusCache.status((_: RequestHeader) => "pages:agentTypeMetric", OK, metricCacheTime) {
    OptionalUserAction.async { implicit request =>
      find[Model](SearchParams.empty, PageParams.empty,
        entities = List(EntityType.HistoricalAgent), facetBuilder = agentTypeFacets)
        .map(jsonResponse[Model])
    }
  }

  def clearCached: Action[AnyContent] = AdminAction { implicit request =>
    // Hack around lack of manual expiry
    cache.remove("pages:agentTypeMetric")
    cache.remove("pages:restrictedMetric")
    cache.remove("pages:repoCountryMetric")
    cache.remove("pages:repoMetric")
    cache.remove("pages:langMetric")
    Redirect(controllers.admin.routes.Home.index())
  }
}
