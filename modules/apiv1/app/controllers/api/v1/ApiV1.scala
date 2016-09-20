package controllers.api.v1

import java.util.concurrent.TimeUnit
import javax.inject.{Singleton, Inject}

import auth.AccountManager
import backend.rest.{PermissionDenied, ItemNotFound}
import backend.{AnonymousUser, DataApi}
import models.api.v1.JsonApiV1._
import controllers.base.{AuthConfigImpl, ControllerHelpers, CoreActionBuilders}
import controllers.generic.Search
import defines.EntityType
import models.base.AnyModel
import models._
import play.api.cache.CacheApi
import play.api.http.HeaderNames
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._
import utils.Page
import utils.search.{SearchConstants, SearchParams, SearchEngine, SearchItemResolver}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class ApiV1 @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  dataApi: DataApi,
  accounts: AccountManager,
  messagesApi: MessagesApi,
  ws: WSClient,
  executionContext: ExecutionContext
) extends CoreActionBuilders
  with ControllerHelpers
  with AuthConfigImpl
  with Search {

  private val logger = play.api.Logger(ApiV1.getClass)

  private implicit val apiUser = AnonymousUser
  private implicit val userOpt: Option[UserProfile] = None

  private val hitsPerSecond = 1000 // basically, no limit at the moment
  private val rateLimitTimeoutDuration: FiniteDuration = Duration(3600, TimeUnit.SECONDS)

  // Authentication: currently a stopgap for releasing with
  // internal testing. Not intended for production since
  // tokens are in config.
  private val authenticated: Boolean = config.getBoolean("ehri.api.v1.authorization.enabled")
    .getOrElse(false)
  import scala.collection.JavaConverters._
  private val authenticationTokens: Seq[String] = config.getStringList("ehri.api.v1.authorization.tokens")
    .map(_.asScala.toSeq).getOrElse(Seq.empty)

  private val apiRoutes = controllers.api.v1.routes.ApiV1
  private val apiSupportedEntities = Seq(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent,
    EntityType.Country
  )

  private def error(status: Int, message: Option[String] = None): Result =
    Status(status)(errorJson(status, message))

  private def errorJson(status: Int, message: Option[String] = None): JsObject = {
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
      authenticationTokens.find(token => authValue ==  "Bearer " + token)
    }
  }

  private object LoggingAuthAction extends ActionBuilder[Request] {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      if (!authenticated) block(request)
      else checkAuthentication(request).map { token =>
        logger.logger.trace(s"API access for $token: ${request.uri}")
        block(request)
      }.getOrElse {
        immediate(error(FORBIDDEN, message = Some("Token required")))
      }
    }
  }

  private object RateLimit extends ActionBuilder[Request] {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      if (checkRateLimit(hitsPerSecond, rateLimitTimeoutDuration)(request)) block(request)
      else immediate(error(TOO_MANY_REQUESTS))
    }
  }

  private object JsonApiCheckAcceptFilter extends ActionFilter[Request] {
    override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
      // If there is an accept media type for JSON-API than one must be unmodified
      val accept: Seq[String] = request.headers.getAll(HeaderNames.ACCEPT).filter(a =>
        a.startsWith(JSONAPI_MIMETYPE))
      if (accept.isEmpty || accept.contains(JSONAPI_MIMETYPE)) immediate(None)
      else immediate(Some(error(NOT_ACCEPTABLE)))
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
      val inc = Seq[Option[_<:AnyModel]](doc.holder, doc.parent).collect{case Some(m) => m}
      if (inc.isEmpty) None else Some(inc)
    case _ => None
  }

  private def searchFilterKey(any: AnyModel): String = any match {
    case repo: Repository => SearchConstants.HOLDER_ID
    case country: Country => SearchConstants.COUNTRY_CODE
    case _ => SearchConstants.PARENT_ID
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

  def index() = JsonApiAction { implicit request =>
    // describe possible actions here...
    Ok(
      Json.obj(
        "meta" -> Json.obj(
          "name" -> "EHRI API V1",
          "routes" -> Json.obj(
            "search" -> (apiRoutes.search().absoluteURL() + "?[q=Text Query]"),
            "fetch" -> apiRoutes.fetch("ITEM-ID").absoluteURL(),
            "search-in" -> (apiRoutes.searchIn("ITEM-ID").absoluteURL() + "?[q=Text Query]")
          ),
          "status" -> "ALPHA: Do not use for production"
        ),
        "jsonapi" -> Json.obj(
          "version" -> "1.0"
        )
      )
    ).as(JSONAPI_MIMETYPE)
  }

  def search(q: Option[String], `type`: Seq[defines.EntityType.Value], page: Int) =
    JsonApiAction.async { implicit request =>
      find[AnyModel](
        defaultParams = SearchParams(query = q, page = Some(page)),
        entities = apiSupportedEntities.filter(e => `type`.isEmpty || `type`.contains(e))
      ).map { r =>
        Ok(Json.toJson(pageData(r.mapItems(_._1).page, p => apiRoutes.search(q, `type`, p).absoluteURL())))
          .as(JSONAPI_MIMETYPE)
      }
    }

  def fetch(id: String) = JsonApiAction.async { implicit request =>
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

  def searchIn(id: String, q: Option[String], `type`: Seq[defines.EntityType.Value], page: Int) = JsonApiAction.async { implicit request =>
    userDataApi.getAny[AnyModel](id).flatMap { item =>
      find[AnyModel](
        filters = Map(searchFilterKey(item) -> id),
        defaultParams = SearchParams(query = q, page = Some(page)),
        entities = apiSupportedEntities.filter(e => `type`.isEmpty || `type`.contains(e))
      ).map { r =>
        Ok(Json.toJson(pageData(r.mapItems(_._1).page,
          p => apiRoutes.searchIn(id, q, `type`, p).absoluteURL(), Some(Seq(item))))
        ).as(JSONAPI_MIMETYPE)
      }
    } recover {
      case e: ItemNotFound => error(NOT_FOUND, e.message)
      case e: PermissionDenied => error(FORBIDDEN)
    }
  }

  override def authenticationFailed(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] =
    immediate(error(UNAUTHORIZED))

  override def authorizationFailed(request: RequestHeader,user: UserProfile)(implicit context: ExecutionContext): Future[Result] =
    immediate(error(FORBIDDEN))

  override def authorizationFailed(request: RequestHeader, user: User, authority: Option[Authority])(implicit context: ExecutionContext): Future[Result] =
    immediate(error(FORBIDDEN))

  override def downForMaintenance(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] =
    immediate(error(SERVICE_UNAVAILABLE))

  override protected def notFoundError(request: RequestHeader,msg: Option[String])(implicit context: ExecutionContext): Future[Result] =
    immediate(error(NOT_FOUND, msg))

  override def staffOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] =
    immediate(error(FORBIDDEN))

  override def verifiedOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] =
    immediate(error(FORBIDDEN))
}
