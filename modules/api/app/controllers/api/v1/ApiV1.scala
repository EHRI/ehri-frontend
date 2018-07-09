package controllers.api.v1

import java.util.concurrent.TimeUnit

import javax.inject.{Inject, Singleton}
import auth.handler.AuthHandler
import services.cypher.Cypher
import controllers.AppComponents
import controllers.base.{ControllerHelpers, CoreActionBuilders, SearchVC}
import controllers.generic.Search
import defines.EntityType
import global.GlobalConfig
import models._
import models.api.v1.JsonApiV1._
import models.base.AnyModel
import play.api.cache.SyncCacheApi
import play.api.http.HeaderNames
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.{Configuration, Logger}
import services.accounts.AccountManager
import services.data._
import utils.{Page, PageParams}
import services.search.SearchConstants._
import services.search._
import views.Helpers

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.duration.{Duration, FiniteDuration}


object ApiV1 {
  val apiSupportedEntities = Seq(
    EntityType.DocumentaryUnit,
    EntityType.VirtualUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent,
    EntityType.Country
  )
}

@Singleton
case class ApiV1 @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  ws: WSClient,
  cypher: Cypher
) extends CoreActionBuilders
  with ControllerHelpers
  with Search
  with SearchVC {

  protected implicit val cache: SyncCacheApi = appComponents.cacheApi
  protected implicit val globalConfig: GlobalConfig = appComponents.globalConfig
  protected val accounts: AccountManager = appComponents.accounts
  protected val dataApi: DataApi = appComponents.dataApi
  protected val config: Configuration = appComponents.config
  protected val authHandler: AuthHandler = appComponents.authHandler
  protected val searchEngine: SearchEngine = appComponents.searchEngine
  protected val searchResolver: SearchItemResolver = appComponents.searchResolver


  import ApiV1._

  private val logger = Logger(ApiV1.getClass)

  private implicit val apiUser: ApiUser = AnonymousUser
  private implicit val userOpt: Option[UserProfile] = None

  private val hitsPerSecond = 1000
  // basically, no limit at the moment
  private val rateLimitTimeoutDuration: FiniteDuration = Duration(1, TimeUnit.SECONDS)

  // i.e. Everything
  private val apiSearchFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("facet.lang"),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      )
    )
  }


  // Authentication: currently a stopgap for releasing with
  // internal testing. Not intended for production since
  // tokens are in config.
  private val authenticated: Boolean = config
    .getOptional[Boolean]("ehri.api.v1.authorization.enabled")
    .getOrElse(false)

  private val authenticationTokens: Seq[String] = config
    .getOptional[Seq[String]]("ehri.api.v1.authorization.tokens")
    .getOrElse(Seq.empty)

  private val apiRoutes = controllers.api.v1.routes.ApiV1

  private def error(status: Int, message: Option[String] = None)(implicit requestHeader: RequestHeader): Result =
    Status(status)(errorJson(status, message))

  private def errorJson(status: Int, message: Option[String] = None)(implicit requestHeader: RequestHeader): JsObject = {
    Json.obj(
      "errors" -> Json.arr(
        JsonApiError(
          status = status.toString,
          title = Messages(s"api.error.$status"),
          detail = message
        )
      )
    )
  }

  private def checkAuthentication(request: RequestHeader): Option[String] = {
    request.headers.get(HeaderNames.AUTHORIZATION).flatMap { authValue =>
      authenticationTokens.find(token => authValue == "Bearer " + token)
    }
  }

  private object LoggingAuthAction extends CoreActionBuilder[Request, AnyContent] {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      if (!authenticated) block(request)
      else checkAuthentication(request).map { token =>
        logger.logger.trace(s"API access for $token: ${request.uri}")
        block(request)
      }.getOrElse {
        immediate(error(FORBIDDEN, message = Some("Token required"))(request))
      }
    }
  }

  private object RateLimit extends CoreActionBuilder[Request, AnyContent] {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      if (checkRateLimit(hitsPerSecond, rateLimitTimeoutDuration)(request)) block(request)
      else immediate(error(TOO_MANY_REQUESTS)(request))
    }
  }

  private object JsonApiCheckAcceptFilter extends CoreActionFilter[Request] {
    override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
      // If there is an accept media type for JSON-API than one must be unmodified
      val accept: Seq[String] = request.headers.getAll(HeaderNames.ACCEPT).filter(a =>
        a.startsWith(JSONAPI_MIMETYPE))
      if (accept.isEmpty || accept.contains(JSONAPI_MIMETYPE)) immediate(None)
      else immediate(Some(error(NOT_ACCEPTABLE)(request)))
    }
  }

  private def JsonApiAction = RateLimit andThen JsonApiCheckAcceptFilter andThen LoggingAuthAction

  implicit def anyModelWrites(implicit request: RequestHeader): Writes[AnyModel] = Writes[AnyModel] {
    case doc: DocumentaryUnit => Json.toJson(
      JsonApiResponseData(
        id = doc.id,
        `type` = doc.isA.toString,
        attributes = Json.toJson(DocumentaryUnitAttrs(doc)),
        relationships = Some(
          Json.toJson(
            DocumentaryUnitRelations(
              holder = doc.holder.map { r =>
                Json.obj("data" -> ResourceIdentifier(r))
              },
              parent = doc.parent.map { r =>
                Json.obj("data" -> ResourceIdentifier(r))
              }
            )
          )
        ),
        links = Some(
          Json.toJson(
            DocumentaryUnitLinks(
              self = apiRoutes.fetch(doc.id).absoluteURL(),
              search = apiRoutes.searchIn(doc.id).absoluteURL(),
              holder = doc.holder.map(r => apiRoutes.fetch(r.id).absoluteURL()),
              parent = doc.parent.map(r => apiRoutes.fetch(r.id).absoluteURL())
            )
          )
        ),
        meta = Some(meta(doc).deepMerge(holderMeta(doc)))
      )
    )
    case vu: VirtualUnit => Json.toJson(
      JsonApiResponseData(
        id = vu.id,
        `type` = vu.isA.toString,
        attributes = Json.toJson(DocumentaryUnitAttrs(vu.asDocumentaryUnit)),
        relationships = Some(
          Json.toJson(
            DocumentaryUnitRelations(
              parent = vu.parent.map(_.asDocumentaryUnit).map { r =>
                Json.obj("data" -> ResourceIdentifier(r))
              }
            )
          )
        ),
        links = Some(
          Json.toJson(
            DocumentaryUnitLinks(
              self = apiRoutes.fetch(vu.id).absoluteURL(),
              search = apiRoutes.searchIn(vu.id).absoluteURL(),
              parent = vu.parent.map(r => apiRoutes.fetch(r.id).absoluteURL())
            )
          )
        ),
        meta = Some(meta(vu).deepMerge(holderMeta(vu)))
      )
    )
    case repo: Repository => Json.toJson(
      JsonApiResponseData(
        id = repo.id,
        `type` = repo.isA.toString,
        attributes = Json.toJson(RepositoryAttrs(repo)),
        relationships = Some(
          Json.toJson(
            RepositoryRelations(
              country = repo.country.map { c =>
                Json.obj("data" -> ResourceIdentifier(c))
              }
            )
          )
        ),
        links = Some(
          Json.toJson(
            RepositoryLinks(
              self = apiRoutes.fetch(repo.id).absoluteURL(),
              search = apiRoutes.searchIn(repo.id).absoluteURL(),
              country = repo.country.map(c => apiRoutes.fetch(c.id).absoluteURL())
            )
          )
        ),
        meta = Some(meta(repo).deepMerge(holderMeta(repo)))
      )
    )
    case agent: HistoricalAgent => Json.toJson(
      JsonApiResponseData(
        id = agent.id,
        `type` = agent.isA.toString,
        attributes = Json.toJson(HistoricalAgentAttrs(agent)),
        links = Some(
          Json.obj(
            "self" -> apiRoutes.fetch(agent.id).absoluteURL()
          )
        ),
        meta = Some(meta(agent))
      )
    )
    case country: Country => Json.toJson(
      JsonApiResponseData(
        id = country.id,
        `type` = country.isA.toString,
        attributes = Json.toJson(CountryAttrs(country)),
        links = Some(
          Json.toJson(
            CountryLinks(
              self = apiRoutes.fetch(country.id).absoluteURL(),
              search = apiRoutes.searchIn(country.id).absoluteURL()
            )
          )
        ),
        meta = Some(meta(country).deepMerge(holderMeta(country)))
      )
    )
    case _ => throw new ItemNotFound()
  }

  private def includedData(any: AnyModel)(implicit requestHeader: RequestHeader): Option[Seq[AnyModel]] = any match {
    case doc: DocumentaryUnit =>
      val inc = Seq[Option[_ <: AnyModel]](doc.holder, doc.parent).collect { case Some(m) => m }
      if (inc.isEmpty) None else Some(inc)
    case _ => None
  }

  private def hierarchySearchFilters(item: AnyModel): Future[Map[String, Any]] = item match {
    case repo: Repository => immediate(Map(SearchConstants.HOLDER_ID -> item.id))
    case country: Country => immediate(Map(SearchConstants.COUNTRY_CODE -> item.id))
    case vc: VirtualUnit => buildChildSearchFilter(vc)
    case _ => immediate(Map(SearchConstants.PARENT_ID -> item.id))
  }

  private def pageData[T <: AnyModel](page: Page[T],
    urlFunc: Int => String,
    included: Option[Seq[AnyModel]] = None)(implicit w: Writes[AnyModel]): JsonApiListResponse =
    JsonApiListResponse(
      data = page.items,
      links = PaginationLinks(
        first = urlFunc(1),
        last = urlFunc(page.numPages),
        prev = if (page.page == 1) Option.empty[String]
        else Some(urlFunc(page.page - 1)),
        next = if (!page.hasMore) Option.empty[String]
        else Some(urlFunc(page.page + 1))
      ),
      included = included,
      meta = Some(Json.obj(
        "total" -> page.total,
        "pages" -> page.numPages
      ))
    )

  def search(`type`: Seq[defines.EntityType.Value], params: SearchParams, paging: PageParams): Action[AnyContent] =
    JsonApiAction.async { implicit request =>
      find[AnyModel](
        params = params,
        paging = paging,
        entities = apiSupportedEntities.filter(e => `type`.isEmpty || `type`.contains(e)),
        facetBuilder = apiSearchFacets
      ).map { r =>
        Ok(Json.toJson(pageData(r.mapItems(_._1).page, p => apiRoutes.search(`type`, params, paging.copy(page = p)).absoluteURL())))
          .as(JSONAPI_MIMETYPE)
      }
    }

  def fetch(id: String): Action[AnyContent] = JsonApiAction.async { implicit request =>
    userDataApi.getAny[AnyModel](id).map { item =>
      Ok(
        Json.toJson(
          JsonApiResponse(
            data = item,
            included = includedData(item)
          )
        )
      ).as(JSONAPI_MIMETYPE)
    } recover {
      case e: ItemNotFound => error(NOT_FOUND, e.message)
      case e: PermissionDenied => error(FORBIDDEN)
    }
  }

  def searchIn(id: String, `type`: Seq[defines.EntityType.Value], params: SearchParams, paging: PageParams): Action[AnyContent] =
    JsonApiAction.async { implicit request =>
      userDataApi.getAny[AnyModel](id).flatMap { item =>
        for {
          filters <- hierarchySearchFilters(item)
          result <- find[AnyModel](
            filters = filters,
            params = params,
            paging = paging,
            entities = apiSupportedEntities.filter(e => `type`.isEmpty || `type`.contains(e)),
            facetBuilder = apiSearchFacets
          )
        } yield Ok(Json.toJson(pageData(result.mapItems(_._1).page,
          p => apiRoutes.searchIn(id, `type`, params, paging.copy(page = p)).absoluteURL(), Some(Seq(item))))
        ).as(JSONAPI_MIMETYPE)
      } recover {
        case e: ItemNotFound => error(NOT_FOUND, e.message)
        case e: PermissionDenied => error(FORBIDDEN)
      }
    }

  override def authenticationFailed(request: RequestHeader): Future[Result] =
    immediate(error(UNAUTHORIZED)(request))

  override def authorizationFailed(request: RequestHeader, user: UserProfile): Future[Result] =
    immediate(error(FORBIDDEN)(request))

  override def downForMaintenance(request: RequestHeader): Future[Result] =
    immediate(error(SERVICE_UNAVAILABLE)(request))

  override protected def notFoundError(request: RequestHeader, msg: Option[String]): Future[Result] =
    immediate(error(NOT_FOUND, msg)(request))

  override def staffOnlyError(request: RequestHeader): Future[Result] =
    immediate(error(FORBIDDEN)(request))

  override def verifiedOnlyError(request: RequestHeader): Future[Result] =
    immediate(error(FORBIDDEN)(request))

  override def loginSucceeded(request: RequestHeader): Future[Result] = {
    val uri = request.session.get(ACCESS_URI).getOrElse(apiRoutes.search().url)
    logger.debug(s"Redirecting logged-in user to: $uri")
    immediate(Redirect(uri).withSession(request.session - ACCESS_URI))
  }

  override def logoutSucceeded(request: RequestHeader): Future[Result] =
    immediate(Redirect(controllers.api.routes.ApiHome.index()))
}
