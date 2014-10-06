package controllers.adminutils

import play.api.libs.concurrent.Execution.Implicits._
import models.{AccountDAO, Isaar}
import models.base.{Description, AnyModel}
import controllers.generic.Search
import play.api.Play.current
import defines.EntityType
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Request, Result}
import play.api.libs.json.Json
import views.Helpers
import utils.search._
import solr.facet.FieldFacetClass

import com.google.inject._
import play.api.cache.{Cache, Cached}
import backend.Backend
import models.json.ClientWriteable


@Singleton
case class Metrics @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends Search {

  private val metricCacheTime = 60 * 60 // 1 hour

  val searchEntities = List(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent
  )

  import client.json._

  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  implicit def itemPageWrites[MT](implicit rd: ClientWriteable[MT]): Writes[ItemPage[MT]] = (
    (__ \ "items").lazyWrite[Seq[MT]](Writes.seq(rd.clientFormat)) and
    (__ \ "page").write[Int] and
    (__ \ "count").write[Int] and
    (__ \ "total").write[Long] and
    (__ \ "facetClasses").lazyWrite(Writes.list[FacetClass[Facet]](FacetClass.facetClassWrites)) and
    (__ \ "spellcheck").writeNullable(
      (__ \ "given").write[String] and
      (__ \ "correction").write[String]
      tupled
    )
  )(unlift(ItemPage.unapply[MT]))


  private def jsonResponse[T](result: QueryResult[T])(implicit request: Request[AnyContent], w: ClientWriteable[T]): Result = {
    render {
      case Accepts.Json() | Accepts.JavaScript() => Ok(Json.obj(
        "page" -> Json.toJson(result.page.copy(items = result.page.items.map(_._1)))(itemPageWrites),
        "params" -> Json.toJson(result.params),
        "appliedFacets" -> Json.toJson(result.facets)
      )).as(play.api.http.ContentTypes.JSON)
      case _ => UnsupportedMediaType
    }
  }

  // For all of the metrics we're just using facet counts,
  // so set the result limit to be zero.
  private val defaultParams = SearchParams(count=Some(0))

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

  def languageOfMaterial = Cached.status(_ => "pages:langMetric", OK, metricCacheTime) {
    userProfileAction.async { implicit userOpt => implicit request =>
      find[AnyModel](
        defaultParams = defaultParams,
        entities = List(EntityType.DocumentaryUnit),
        facetBuilder = langCountFacets
      ).map(jsonResponse[AnyModel])
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

  def holdingRepository = Cached.status(_ => "pages:repoMetric", OK, metricCacheTime) {
    userProfileAction.async { implicit userOpt => implicit request =>
      find[AnyModel](
        defaultParams = defaultParams,
        entities = List(EntityType.DocumentaryUnit),
        facetBuilder = holdingRepoFacets
      ).map(jsonResponse[AnyModel])
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

  def repositoryCountries = Cached.status(_ => "pages:repoCountryMetric", OK, metricCacheTime) {
    userProfileAction.async { implicit userOpt => implicit request =>
      find[AnyModel](
        defaultParams = defaultParams,
        entities = List(EntityType.Repository),
        facetBuilder = countryRepoFacets
      ).map(jsonResponse[AnyModel])
    }
  }

  private val restrictedFacets: FacetBuilder = { implicit request =>
    List(
      // Historical agent type
      FieldFacetClass(
        key=solr.SolrConstants.RESTRICTED_FIELD,
        name=Messages("search.isRestricted"),
        param="restricted",
        render=s => Messages("restricted" + "." + s)
      )
    )
  }

  def restricted = Cached.status(_ => "pages:restrictedMetric", OK, metricCacheTime) {
    userProfileAction.async { implicit userOpt => implicit request =>
      find[AnyModel](
        defaultParams = defaultParams,
        entities = List(EntityType.HistoricalAgent,
            EntityType.DocumentaryUnit, EntityType.HistoricalAgent),
        facetBuilder = restrictedFacets
      ).map(jsonResponse[AnyModel])
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

  def agentTypes = Cached.status(_ => "pages:agentTypeMetric", OK, metricCacheTime) {
    userProfileAction.async { implicit userOpt => implicit request =>
      find[AnyModel](
        entities = List(EntityType.HistoricalAgent),
        facetBuilder = agentTypeFacets
      ).map(jsonResponse[AnyModel])
    }
  }

  def clearCached = adminAction { implicit  userOpt => implicit request =>
    // Hack around lack of manual expiry
    Cache.remove("pages:agentTypeMetric")
    Cache.remove("pages:restrictedMetric")
    Cache.remove("pages:repoCountryMetric")
    Cache.remove("pages:repoMetric")
    Cache.remove("pages:langMetric")
    Redirect(globalConfig.routeRegistry.default)
  }
}
