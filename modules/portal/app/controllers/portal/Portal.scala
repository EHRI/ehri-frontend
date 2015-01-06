package controllers.portal

import play.api.Play.current
import controllers.generic.Search
import models._
import models.base.AnyModel
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._
import views.html.p
import utils.search._
import play.api.cache.Cached
import defines.EntityType
import play.api.libs.ws.WS
import play.twirl.api.Html
import solr.SolrConstants
import backend.Backend
import controllers.base.SessionPreferences
import utils._

import com.google.inject._
import views.html.errors.pageNotFound
import org.joda.time.DateTime
import caching.FutureCache
import controllers.portal.base.PortalController
import scala.concurrent.Future
import backend.rest.cypher.CypherDAO
import backend.rest.SearchDAO
import play.api.libs.json.{Json, JsString}

@Singleton
case class Portal @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
    userDAO: AccountDAO)
  extends PortalController
  with Search
  with FacetConfig
  with SessionPreferences[SessionPrefs]
  with Secured {

  val defaultPreferences = new SessionPrefs

  private val portalRoutes = controllers.portal.routes.Portal

  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads
  private val defaultSearchTypes = List(
    EntityType.Repository,
    EntityType.DocumentaryUnit,
    EntityType.HistoricalAgent,
    EntityType.Country
  )

  def personalisedActivity = WithUserAction.async{ implicit request =>
    // NB: Increasing the limit by 1 over the default so we can
    // detect if there are additional items to display
    val listParams = RangeParams.fromRequest(request)
    val eventFilter = SystemEventParams.fromRequest(request)
      .copy(eventTypes = activityEventTypes)
      .copy(itemTypes = activityItemTypes)
    backend.listEventsForUser[SystemEvent](request.profile.id, listParams, eventFilter).map { events =>
      if (isAjax) Ok(p.activity.eventItems(events))
        .withHeaders("activity-more" -> events.more.toString)
      else Ok(p.activity.activity(events, listParams))
    }
  }

  def search = UserBrowseAction.async { implicit request =>
    find[AnyModel](
      defaultParams = SearchParams(
        entities = defaultSearchTypes, sort = Some(SearchOrder.Score)),
      facetBuilder = globalSearchFacets, mode = SearchMode.DefaultNone
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.search(page, params, facets, portalRoutes.search(), request.watched))
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
    FutureCache.getOrElse("index:metrics", 60 * 5) {
      find[AnyModel](
        defaultParams = SearchParams(
          // we don't need results here because we're only using the facets
          count = Some(0),
          entities = defaultSearchTypes
        ),
        facetBuilder = entityMetrics
      ).map(_.page.facets)
    }.map(facets => Ok(p.portal(Stats(facets))))
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
    backend.history[SystemEvent](id, params, filters).map { events =>
      if (isAjax && modal) Ok(p.activity.itemActivityModal(events))
      else if (isAjax) Ok(p.activity.itemEventItems(events))
        .withHeaders("activity-more" -> events.more.toString)
      else Ok(p.activity.itemActivity(events, params))
    }
  }

  def placeholder = Cached.status(_ => "pages:portalPlaceholder", OK, 60 * 60) {
    Action { implicit request =>
      Ok(views.html.placeholder())
    }
  }

  def dataPolicy = Cached("pages:portalDataPolicy") {
    OptionalUserAction.apply { implicit request =>
      Ok(p.dataPolicy())
    }
  }

  def terms = OptionalUserAction.apply { implicit request =>
    Ok(p.terms())
  }

  def about = OptionalUserAction.apply { implicit request =>
    Ok(p.about())
  }

  def contact = OptionalUserAction.apply { implicit request =>
    Ok(p.contact())
  }

  case class NewsItem(title: String, link: String, description: Html, pubDate: Option[DateTime] = None)

  final val NUM_NEWS_ITEMS = 2

  object NewsItem {
    import scala.util.control.Exception._
    import org.joda.time.format.DateTimeFormat
    def fromRss(feed: String): Seq[NewsItem] = {
      val pat = DateTimeFormat.forPattern("EEE, dd MMM yyyy H:m:s Z")
      (scala.xml.XML.loadString(feed) \\ "item").take(NUM_NEWS_ITEMS).map { item =>
        NewsItem(
          title = (item \ "title").text,
          link = (item \ "link").text,
          description = Html((item \ "description").text),
          pubDate = allCatch.opt(DateTime.parse((item \ "pubDate").text, pat))
        )
      }
    }
  }

  def newsFeed = Cached.status(_ => "pages.newsFeed", OK, 60 * 60) {
    Action.async { request =>
      WS.url("http://www.ehri-project.eu/rss.xml").get().map { r =>
        Ok(p.newsFeed(NewsItem.fromRss(r.body)))
      }
    }
  }
}

