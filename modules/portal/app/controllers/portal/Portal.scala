package controllers.portal

import java.util.concurrent.TimeUnit
import javax.inject._

import backend.HtmlPages
import controllers.Components
import controllers.generic.Search
import controllers.portal.base.PortalController
import defines.EntityType
import models._
import models.base.AnyModel
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import utils._
import utils.caching.FutureCache
import utils.search._
import views.html.errors.pageNotFound

import scala.concurrent.Future
import scala.concurrent.duration.Duration


@Singleton
case class Portal @Inject()(
  components: Components,
  htmlPages: HtmlPages,
  ws: WSClient
) extends PortalController
  with Search
  with FacetConfig {

  private val portalRoutes = controllers.portal.routes.Portal

  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads
  private val defaultSearchTypes = List(
    EntityType.Repository,
    EntityType.DocumentaryUnit,
    EntityType.VirtualUnit,
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
    Redirect(referrer)
      .withPreferences(request.preferences.copy(language = Some(lang)))
  }

  def personalisedActivity = WithUserAction.async{ implicit request =>
    // NB: Increasing the limit by 1 over the default so we can
    // detect if there are additional items to display
    val listParams = RangeParams.fromRequest(request)
    val eventFilter = SystemEventParams.fromRequest(request)
      .copy(eventTypes = activityEventTypes)
      .copy(itemTypes = activityItemTypes)
    userDataApi.userEvents[SystemEvent](request.user.id, listParams, eventFilter).map { events =>
      if (isAjax) Ok(views.html.activity.eventItems(events))
        .withHeaders("activity-more" -> events.more.toString)
      else Ok(views.html.activity.activity(events, listParams))
    }
  }

  def search = UserBrowseAction.async { implicit request =>
    find[AnyModel](
      defaultParams = SearchParams(sort = Some(SearchOrder.Score)),
      facetBuilder = globalSearchFacets, mode = SearchMode.DefaultNone,
      entities = defaultSearchTypes
    ).map { result =>
      Ok(views.html.search.globalSearch(result, portalRoutes.search(), request.watched))
    }
  }


  /**
   * Quick filter action that searches applies a 'q' string filter to
   * only the name_ngram field and returns an id/name pair.
   * @return
   */
  def filterItems = OptionalUserAction.async { implicit request =>
    filter().map { page =>
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
  def index = OptionalUserAction.async { implicit request =>
    FutureCache.getOrElse("index:metrics", Duration(60 * 5, TimeUnit.SECONDS)) {
      find[AnyModel](
        defaultParams = SearchParams(
          // we don't need results here because we're only using the facets
          count = Some(0),
          entities = defaultSearchTypes
        ),
        facetBuilder = entityMetrics,
        extra = Map("facet.limit" -> "-1")
      ).map(_.facetClasses)
    }.map(facets => Ok(views.html.portal(Stats(facets))))
  }

  def browseItem(entityType: EntityType.Value, id: String) = Action { implicit request =>
    val call = views.p.Helpers.linkTo(entityType, id)
    if (call.url.equals("#"))
      NotFound(controllers.renderError("errors.pageNotFound", pageNotFound()))
    else Redirect(call)
  }

  def itemHistory(id: String, modal: Boolean = false) = OptionalUserAction.async { implicit request =>
    val params: RangeParams = RangeParams.fromRequest(request)
    val filters = SystemEventParams.fromRequest(request)
    userDataApi.history[SystemEvent](id, params, filters).map { events =>
      if (isAjax && modal) Ok(views.html.activity.itemActivityModal(events))
      else if (isAjax) Ok(views.html.activity.eventItems(events))
        .withHeaders("activity-more" -> events.more.toString)
      else Ok(views.html.activity.itemActivity(events, params))
    }
  }

  def eventDetails(id: String) = OptionalUserAction.async { implicit request =>
    val params: PageParams = PageParams.fromRequest(request)
    val eventF: Future[SystemEvent] = userDataApi.get[SystemEvent](id)
    val subjectsF: Future[Page[AnyModel]] = userDataApi.subjectsForEvent[AnyModel](id, params)
    for {
      event <- eventF
      subjects <- subjectsF
    } yield {
      Ok(views.html.activity.eventSubjects(event, subjects, params))
    }
  }

  def dataPolicy = OptionalUserAction.apply { implicit request =>
    Ok(views.html.dataPolicy())
  }

  def terms = OptionalUserAction.apply { implicit request =>
    Ok(views.html.terms())
  }

  def about = OptionalUserAction.apply { implicit request =>
    Ok(views.html.about())
  }

  def contact = OptionalUserAction.apply { implicit request =>
    Ok(views.html.contact())
  }

  def externalFeed(key: String) = components.statusCache.status(_ => s"pages.$key", OK, 60 * 60) {
    Action.async { implicit request =>
      futureItemOr404 {
        config.getString(s"ehri.portal.externalFeed.$key.rss").map { url =>
          val numItems = config.getInt(s"ehri.portal.externalFeed.$key.numItems").getOrElse(2)
          ws.url(url).get().map { r =>
            Ok(views.html.rssFeed(RssFeed(r.body, numItems)))
          }
        }
      }
    }
  }

  def externalPage(key: String) = OptionalUserAction.async { implicit request =>
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
}

