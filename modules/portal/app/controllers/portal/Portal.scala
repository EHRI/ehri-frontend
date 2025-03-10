package controllers.portal

import controllers.AppComponents
import controllers.generic.Search
import controllers.portal.base.PortalController
import cookies.SessionPrefs
import forms.AccountForms
import models._
import play.api.cache.{AsyncCacheApi, Cached}
import play.api.i18n.{Lang, Messages}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.datamodel.EntityTypeMetadataService
import services.htmlpages.HtmlPages
import services.search._
import utils._
import views.html.errors.pageNotFound

import java.util.IllformedLocaleException
import javax.inject._
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt


@Singleton
case class Portal @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  htmlPages: HtmlPages,
  ws: WSClient,
  fc: FacetConfig,
  accountForms: AccountForms,
  asyncCache: AsyncCacheApi,
  statusCache: Cached,
  entityTypeMetadataService: EntityTypeMetadataService,
) extends PortalController
  with Search {

  private val portalRoutes = controllers.portal.routes.Portal

  private val defaultSearchTypes = List(
    EntityType.Repository,
    EntityType.DocumentaryUnit,
    EntityType.Country,
    EntityType.HistoricalAgent,
    EntityType.Concept
  )

  def prefs: Action[AnyContent] = Action { implicit request =>
    Ok(Json.toJson(preferences))
  }

  def updatePrefs(): Action[AnyContent] = Action { implicit request =>
    val current = request.preferences
    SessionPrefs.updateForm(current).bindFromRequest().fold(
      errors => BadRequest(errors.errorsAsJson),
      updated => {
        (if (isAjax) Ok(Json.toJson(updated))
        else Redirect(portalRoutes.prefs())).withPreferences(updated)
      }
    )
  }

  def changeLocale(lang: String): Action[AnyContent] = Action { implicit request =>
    val referrer = request.headers.get(REFERER).getOrElse("/")
    try messagesApi.setLang(Redirect(referrer), Lang(lang)) catch {
      case _: IllformedLocaleException => Redirect(referrer)
    }
  }

  def personalisedActivity(params: SystemEventParams, range: RangeParams): Action[AnyContent] = WithUserAction.async { implicit request =>
    // NB: Increasing the limit by 1 over the default so we can
    // detect if there are additional items to display
    val eventFilter = params
      .copy(eventTypes = activityEventTypes)
      .copy(itemTypes = activityItemTypes)
    userDataApi.userEvents[SystemEvent](request.user.id, range, eventFilter).map { events =>
      if (isAjax) Ok(views.html.activity.eventItems(events))
        .withHeaders("activity-more" -> events.more.toString)
      else Ok(views.html.activity.activity(events, range))
    }
  }

  def search(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    find[Model](params, paging,
      facetBuilder = fc.globalSearchFacets, mode = SearchMode.DefaultNone,
      sort = SearchSort.Score,
      entities = defaultSearchTypes).map { result =>
      Ok(views.html.search.globalSearch(result, portalRoutes.search(), request.watched))
    }
  }


  /**
    * Quick filter action that searches applies a 'q' string filter to
    * only the name_ngram field and returns an id/name pair.
    */
  def filterItems(params: SearchParams, paging: PageParams): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    filter(params, paging).map { page =>
      Ok(Json.obj(
        "numPages" -> page.numPages,
        "page" -> page.page,
        "items" -> page.items
      ))
    }
  }

  /**
    * Portal index. Currently this just shows an overview of the data
    * extracted from the search engine facet counts for different
    * types.
    */
  def index: Action[AnyContent] = OptionalUserAction.async { implicit request =>
    getStats.map(s => Ok(views.html.index(s, accountForms)))
  }

  def browseItem(entityType: EntityType.Value, id: String): Action[AnyContent] = OptionalUserAction { implicit request =>
    val call = views.Helpers.linkTo(entityType, id)
    if (call.url.equals("#"))
      NotFound(controllers.renderError("errors.pageNotFound", pageNotFound()))
    else Redirect(call)
  }

  def itemHistory(id: String, params: SystemEventParams, range: RangeParams, modal: Boolean = false): Action[AnyContent] =
    OptionalUserAction.async { implicit request =>
      userDataApi.history[SystemEvent](id, range, params).map { events =>
        if (isAjax && modal) Ok(views.html.activity.itemActivityModal(events))
        else if (isAjax) Ok(views.html.activity.eventItems(events))
          .withHeaders("activity-more" -> events.more.toString)
        else Ok(views.html.activity.itemActivity(events, range))
      }
    }

  def eventDetails(id: String, paging: PageParams): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    val eventF: Future[SystemEvent] = userDataApi.get[SystemEvent](id)
    val subjectsF: Future[Page[Model]] = userDataApi.subjectsForEvent[Model](id, paging)
    for {
      event <- eventF
      subjects <- subjectsF
    } yield {
      Ok(views.html.activity.eventSubjects(event, subjects, paging))
    }
  }

  def dataPolicy: Action[AnyContent] = OptionalUserAction.async { implicit request =>
    externalPage("data-policy").apply(request)
  }

  def terms: Action[AnyContent] = OptionalUserAction.async { implicit request =>
    externalPage("terms").apply(request)
  }

  def about: Action[AnyContent] = OptionalUserAction.apply { implicit request =>
    Ok(views.html.about())
  }

  def contact: Action[AnyContent] = OptionalUserAction.apply { implicit request =>
    Ok(views.html.contact())
  }

  def dataModel(): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    for {
      ets <- entityTypeMetadataService.list()
      fields <- entityTypeMetadataService.listFields()
      tmpl <- entityTypeMetadataService.templates()
    } yield {
      Ok(views.html.dataModel(ets, fields, tmpl))
    }
  }

  def externalFeed(key: String): EssentialAction = statusCache.status((_: RequestHeader) => s"pages.$key", OK, 60.minutes) {
    Action.async { implicit request =>
      futurePageOr404 {
        config.getOptional[String](s"ehri.portal.externalFeed.$key.rss").map { url =>
          val numItems = config.getOptional[Int](s"ehri.portal.externalFeed.$key.numItems").getOrElse(2)
          ws.url(url).get().map { r =>
            Ok(views.html.rssFeed(RssFeed(r.body, numItems)))
          }
        }
      }
    }
  }

  def externalPage(key: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    futurePageOr404 {
      htmlPages.get(key, noCache = request.getQueryString("noCache").isDefined).map { futureData =>
        futureData.map { case (title, css, html) =>
          val titleKey = s"pages.external.$key.title"
          val descKey = s"pages.external.$key.description"
          val i18nTitle = if (Messages.isDefinedAt(titleKey)) Messages(titleKey) else title
          val i18nMeta = Map("description" -> (if(Messages.isDefinedAt(descKey)) Messages(descKey) else ""))
          Ok(views.html.layout.textLayout(i18nTitle, meta = i18nMeta, styles = css)(html))
        }
      }
    }
  }

  private def getStats(implicit request: RequestHeader): Future[Stats] =
    asyncCache.getOrElseUpdate("index:metrics", 10.minutes) {
      // Assume no user for fetching global stats
      implicit val userOpt: Option[UserProfile] = None
      find[Model](
        // we don't need results here because we're only using
        // json facet analytics...
        params = SearchParams.empty,
        paging = PageParams(limit = 0),
        entities = defaultSearchTypes,
        extra = Map("json.facet" -> Stats.analyticsQuery)
      ).map(r => Stats(r.facetInfo))
    }
}

