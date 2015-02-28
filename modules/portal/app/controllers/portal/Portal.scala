package controllers.portal

import java.io.{StringReader, BufferedReader}

import auth.AccountManager
import org.w3c.dom.css.{CSSRule, CSSRuleList}
import play.api.Play.current
import controllers.generic.Search
import models._
import models.base.AnyModel
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import views.html.p
import utils.search._
import play.api.cache.{Cache, Cached}
import defines.EntityType
import play.api.libs.ws.WS
import play.twirl.api.Html
import backend.Backend
import utils._

import com.google.inject._
import views.html.errors.pageNotFound
import org.joda.time.DateTime
import caching.FutureCache
import controllers.portal.base.PortalController
import play.api.libs.json.Json

import scala.concurrent.Future

@Singleton
case class Portal @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver, backend: Backend,
    accounts: AccountManager, pageRelocator: utils.MovedPageLookup)
  extends PortalController
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
    backend.listEventsForUser[SystemEvent](request.user.id, listParams, eventFilter).map { events =>
      if (isAjax) Ok(p.activity.eventItems(events))
        .withHeaders("activity-more" -> events.more.toString)
      else Ok(p.activity.activity(events, listParams))
    }
  }

  def search = UserBrowseAction.async { implicit request =>
    find[AnyModel](
      defaultParams = SearchParams(sort = Some(SearchOrder.Score)),
      facetBuilder = globalSearchFacets, mode = SearchMode.DefaultNone,
      entities = defaultSearchTypes
    ).map { result =>
      Ok(p.search(result, portalRoutes.search(), request.watched))
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
        facetBuilder = entityMetrics,
        extra = Map("facet.limit" -> "-1")
      ).map(_.facetClasses)
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

  def dataPolicy = OptionalUserAction.apply { implicit request =>
    Ok(p.dataPolicy())
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

  def docPage(key: String) = OptionalUserAction.async { implicit request =>

    def scopedCss(scope: String, css: String): String = {
      import org.w3c.dom.css.CSSStyleSheet
      import com.steadystate.css.parser.CSSOMParser
      import org.w3c.css.sac.InputSource

      val cssParser = new CSSOMParser()
      val stylesheet: CSSStyleSheet = cssParser
        .parseStyleSheet(new InputSource(new StringReader(css)), null, null)
      val buf = new StringBuilder
      val rules: CSSRuleList = stylesheet.getCssRules
      for {i <- 0 until rules.getLength} {
        buf.append(scope)
        buf.append(" ")
        buf.append(rules.item(i).getCssText)
      }
      buf.append("h1 h2 h3 h4 h5 h6 {font-family: serif;}")
      buf.toString()
    }

    def googleDocBody(url: String): Future[Html] = {
      WS.url(url).withQueryString(
        "e" -> "download",
        "exportFormat" -> "html",
        "format" -> "html"
      ).get().map { r =>
        import org.jsoup.Jsoup

        val doc = Jsoup.parse(r.body)
        val styleTags = doc.getElementsByTag("style")
        val cssScope = "g-doc"
        val newCssData = scopedCss(s".$cssScope", styleTags.html())
        val newCss = styleTags.html(newCssData)
        val body = doc
          .body()
          .addClass(cssScope)
          .prepend(newCss.outerHtml())
          .tagName("div")
        Html(body.outerHtml())
      }
    }

    futureItemOr404 {
      current.configuration.getString(s"pages.external.google.$key").map { url =>
        val pageId: String = s"googledoc:$url"
        FutureCache.getOrElse(pageId, 60 * 10)(googleDocBody(url)).map { data =>
          val title = Messages(s"pages.external.$key.title")
          val meta = Map("description" -> Messages(s"pages.external.$key.description"))
          Ok(views.html.p.layout.portalLayout(title, meta = meta)(data))
        }
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

