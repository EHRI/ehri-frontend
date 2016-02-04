package backend.googledocs

import javax.inject.Inject

import backend.HtmlPages
import backend.rest.{PermissionDenied, ItemNotFound}
import play.api.http.Status
import play.api.i18n.Messages
import play.api.libs.ws.WSClient
import play.twirl.api.Html
import utils.caching.FutureCache

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._

/**
 * Google docs implementation of an Html Page.
 *
 * We currently have to do some pretty janky things
 * to get this to work properly, line re-writing the
 * page css to put all the selectors in a scope so it
 * doesn't affect the rest of the page.
 */
case class GoogleDocsHtmlPages @Inject ()(implicit cache: play.api.cache.CacheApi, app: play.api.Application, ws: WSClient) extends HtmlPages {
  private def googleDocBody(url: String): Future[(Html, Html)] = {
    ws.url(url).withQueryString(
      "e" -> "download",
      "exportFormat" -> "html",
      "format" -> "html"
    ).get().map { r =>

      r.status match {
        case Status.NOT_FOUND => throw new ItemNotFound()
        case Status.FORBIDDEN => throw new PermissionDenied()
        case _ =>
      }

      import org.jsoup.Jsoup

      val doc = Jsoup.parse(r.body)
      val body = doc
        .body()
        .tagName("div")
        .addClass("external-page")

      // Published Google docs have style embedded in the body
      // a header div with the document name, and a footer
      // - remove these.
      body.select("style").remove()
      body.select("div#header").remove()
      body.select("div#footer").remove()

      Html("") -> Html(body.outerHtml())
    }
  }

  override def get(key: String, noCache: Boolean = false)(implicit messages: Messages): Option[Future[(Html, Html)]] = {
    def getUrl: Option[String] =
      app.configuration.getString(s"pages.external.google.$key.${messages.lang.code}") orElse
          app.configuration.getString(s"pages.external.google.$key.default")

    getUrl.map { url =>
      val cacheKey = s"htmlpages.googledocs.$key.${messages.lang.code}"
      val cacheTime = (60 * 60).seconds
      if (noCache) googleDocBody(url).map { data =>
        cache.set(cacheKey, data, cacheTime)
        data
      } else FutureCache.getOrElse(cacheKey, cacheTime) {
        googleDocBody(url)
      }
    }
  }
}
