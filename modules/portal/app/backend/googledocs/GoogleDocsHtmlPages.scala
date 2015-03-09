package backend.googledocs

import java.io.StringReader

import backend.HtmlPages
import backend.rest.{PermissionDenied, ItemNotFound}
import caching.FutureCache
import play.api.cache.Cache
import play.api.http.Status
import play.api.i18n.Lang
import play.api.libs.ws.WS
import play.twirl.api.Html

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Google docs implementation of an Html Page.
 *
 * We currently have to do some pretty janky things
 * to get this to work properly, line re-writing the
 * page css to put all the selectors in a scope so it
 * doesn't affect the rest of the page.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class GoogleDocsHtmlPages()(implicit app: play.api.Application) extends HtmlPages {
  private def googleDocBody(url: String): Future[(Html, Html)] = {
    WS.url(url).withQueryString(
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
      Html("") -> Html(body.outerHtml())
    }
  }

  override def get(key: String, noCache: Boolean = false)(implicit lang: Lang): Option[Future[(Html, Html)]] = {
    def getUrl: Option[String] =
      app.configuration.getString(s"pages.external.google.$key.${lang.code}") orElse
          app.configuration.getString(s"pages.external.google.$key.default")


    getUrl.map { url =>
      val cacheKey = s"htmlpages.googledocs.$key"
      val cacheTime = 60 * 60 // 1 hour
      if (noCache) googleDocBody(url).map { data =>
        Cache.set(cacheKey, data, cacheTime)
        data
      } else FutureCache.getOrElse(cacheKey, cacheTime) {
        googleDocBody(url)
      }
    }
  }
}
