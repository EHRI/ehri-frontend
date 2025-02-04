package services.htmlpages

import play.api.cache.AsyncCacheApi
import play.api.http.Status
import play.api.i18n.Messages
import play.api.libs.ws.WSClient
import play.twirl.api.Html
import services.data.{ItemNotFound, PermissionDenied}

import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Google docs implementation of an Html Page.
 *
 * We currently have to do some pretty janky things
 * to get this to work properly, line re-writing the
 * page css to put all the selectors in a scope so it
 * doesn't affect the rest of the page.
 */
case class GoogleDocsHtmlPages @Inject ()(ws: WSClient, config: play.api.Configuration)(
    implicit cache: AsyncCacheApi, executionContext: ExecutionContext) extends HtmlPages {

  private def googleDocBody(url: String): Future[(String, Html, Html)] = {
    ws.url(url).addQueryStringParameters(
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

      val title = doc.title().replace(" - Google Docs", "")

      val body = doc
        .body()
        .tagName("div")
        .addClass("external-page")

      // Published Google docs have style embedded in the body
      // a header div with the document name, and a footer
      // - remove these.
      body.select("style").remove()
      body.select("div#header").remove()
      body.select("div#banners").remove()
      body.select("div#footer").remove()

      // Google Docs published pages rewrite URLs to route through Google's servers
      // - rewrite these to point to the original URL
      body.select("a[href]").forEach { link =>
        val href = link.attr("href")
        if (href.startsWith("https://www.google.com/url?q=")) {
          // Pluck out the `q` parameter from the URL
          val url = java.net.URLDecoder.decode(href.split("q=")(1).split("&")(0), "UTF-8")
          link.attr("href", url)
        }
      }

      (title, Html(""), Html(body.outerHtml()))
    }
  }

  override def get(key: String, noCache: Boolean = false)(implicit messages: Messages): Option[Future[(String, Html, Html)]] = {
    def getUrl: Option[String] =
      config.getOptional[String](s"pages.external.google.$key.${messages.lang.code}") orElse
          config.getOptional[String](s"pages.external.google.$key.default")

    getUrl.map { url =>
      val cacheKey = s"htmlpages.googledocs.$key.${messages.lang.code}"
      val cacheTime = (60 * 60).seconds
      if (noCache) googleDocBody(url).flatMap { data =>
        cache.set(cacheKey, data, cacheTime).map(_ => data)
      } else cache.getOrElseUpdate(cacheKey, cacheTime) {
        googleDocBody(url)
      }
    }
  }
}
