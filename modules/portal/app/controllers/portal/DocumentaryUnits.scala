package controllers.portal

import auth.AccountManager
import backend.rest.cypher.Cypher
import models.base.AnyModel
import play.api.cache.CacheApi
import play.api.http.{HeaderNames, ContentTypes}
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import backend.Backend
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.DocumentaryUnit
import play.api.libs.iteratee.Enumerator
import play.api.mvc.RequestHeader
import utils.MovedPageLookup
import utils.search._
import views.MarkdownRenderer

import scala.concurrent.Future.{successful => immediate}

@Singleton
case class DocumentaryUnits @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher
) extends PortalController
  with Generic[DocumentaryUnit]
  with Search
  with FacetConfig {

  private val portalDocRoutes = controllers.portal.routes.DocumentaryUnits

  private def filterKey(implicit request: RequestHeader): String =
    if (!hasActiveQuery(request)) SearchConstants.PARENT_ID else SearchConstants.ANCESTOR_IDS

  def searchAll = UserBrowseAction.async { implicit request =>
    val filters = if (!hasActiveQuery(request))
      Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String,Any]

    find[AnyModel](
      filters = filters,
      facetBuilder = docSearchFacets,
      entities = Seq(EntityType.DocumentaryUnit, EntityType.VirtualUnit)
    ).map { result =>
      Ok(views.html.documentaryUnit.list(result, portalDocRoutes.searchAll(),
        request.watched))
    }
  }

  def browse(id: String) = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(views.html.documentaryUnit.itemDetails(request.item, request.annotations, request.links, request.watched)))
    else findType[DocumentaryUnit](
      filters = Map(filterKey -> request.item.id),
      facetBuilder = localDocFacets,
      defaultOrder = SearchOrder.Id
    ).map { result =>
      Ok(views.html.documentaryUnit.show(request.item, result,  request.annotations,
        request.links, portalDocRoutes.search(id), request.watched))
    }
  }

  def search(id: String) = GetItemAction(id).async { implicit request =>
    findType[DocumentaryUnit](
      filters = Map(filterKey -> request.item.id),
      facetBuilder = localDocFacets,
      defaultOrder = SearchOrder.Id
    ).map { result =>
      if (isAjax) Ok(views.html.documentaryUnit.childItemSearch(request.item, result,
        portalDocRoutes.search(id), request.watched))
      else Ok(views.html.documentaryUnit.search(request.item, result,
        portalDocRoutes.search(id), request.watched))
    }
  }

  def export(id: String) = OptionalUserAction.async { implicit request =>
    val format = "ead" // Hardcoded for now!
    val params = request.queryString.filterKeys(_ == "lang")
    userBackend.stream(s"classes/${EntityType.DocumentaryUnit}/$id/$format", params = params).map { sr =>
      val ct = sr.headers.headers.get(HeaderNames.CONTENT_TYPE)
        .flatMap(_.headOption).getOrElse(ContentTypes.XML)
      Status(sr.headers.status).chunked(sr.body).as(ct)
        .withHeaders(sr.headers.headers.map(s => (s._1, s._2.head)).toSeq: _*)
    }
  }
}
