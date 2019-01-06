package controllers.portal

import java.util.IllformedLocaleException
import java.util.concurrent.TimeUnit

import controllers.AppComponents
import controllers.generic.Search
import controllers.portal.base.PortalController
import defines.EntityType
import javax.inject._
import models._
import models.base.Model
import play.api.i18n.{Lang, Messages}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.htmlpages.HtmlPages
import services.search._
import utils._
import utils.caching.FutureCache
import views.html.errors.pageNotFound

import scala.concurrent.Future
import scala.concurrent.duration.Duration


@Singleton
case class Portal @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  htmlPages: HtmlPages,
  ws: WSClient,
  fc: FacetConfig
) extends PortalController
  with Search {

  private val portalRoutes = controllers.portal.routes.Portal

  private val defaultSearchTypes = List(
    EntityType.Repository,
    EntityType.DocumentaryUnit,
    EntityType.Country
  )

  def prefs = Action { implicit request =>
    Ok(Json.toJson(preferences))
  }

  def updatePrefs() = Action { implicit request =>
    val current = request.preferences
    SessionPrefs.updateForm(current).bindFromRequest.fold(
      errors => BadRequest(errors.errorsAsJson),
      updated => {
        (if (isAjax) Ok(Json.toJson(updated))
        else Redirect(portalRoutes.prefs())).withPreferences(updated)
      }
    )
  }

  def changeLocale(lang: String) = Action { implicit request =>
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
    getStats.map(s => Ok(views.html.portal(s)))
  }

  def browseItem(entityType: EntityType.Value, id: String) = Action { implicit request =>
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

  def dataPolicy: Action[AnyContent] = OptionalUserAction.apply { implicit request =>
    Ok(views.html.dataPolicy())
  }

  def terms: Action[AnyContent] = OptionalUserAction.apply { implicit request =>
    Ok(views.html.terms())
  }

  def about: Action[AnyContent] = OptionalUserAction.apply { implicit request =>
    Ok(views.html.about())
  }

  def contact: Action[AnyContent] = OptionalUserAction.apply { implicit request =>
    Ok(views.html.contact())
  }

  def externalFeed(key: String): EssentialAction = appComponents.statusCache.status((_: RequestHeader) => s"pages.$key", OK, 60 * 60) {
    Action.async { implicit request =>
      futureItemOr404 {
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
    futureItemOr404 {
      htmlPages.get(key, noCache = request.getQueryString("noCache").isDefined).map { futureData =>
        futureData.map { case (css, html) =>
          val title = Messages(s"pages.external.$key.title")
          val meta = Map("description" -> Messages(s"pages.external.$key.description"))
          Ok(views.html.layout.textLayout(title, meta = meta, styles = css)(html))
        }
      }
    }
  }

  private def getStats(implicit request: RequestHeader): Future[Stats] =
    FutureCache.getOrElse("index:metrics", Duration(5, TimeUnit.MINUTES)) {
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

