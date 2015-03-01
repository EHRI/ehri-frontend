package backend.googledocs

import java.io.StringReader

import backend.HtmlPages
import backend.rest.{PermissionDenied, ItemNotFound}
import caching.FutureCache
import play.api.cache.Cache
import play.api.http.Status
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
  private def scopedCss(scope: String, css: String): String = {
    import org.w3c.dom.css.CSSStyleSheet
    import com.steadystate.css.parser.CSSOMParser
    import org.w3c.dom.css.CSSRuleList
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
    buf.toString()
  }

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
      val styleTags = doc.getElementsByTag("style")
      val cssScope = "g-doc"
      val newCssData = scopedCss(s".$cssScope", styleTags.html())
      val newCss = styleTags.attr("scoped", "true").html(newCssData)
      val body = doc
        .body()
        .addClass(cssScope)
        .prepend(newCss.outerHtml())
        .tagName("div")
      val css = Html(
        """<style>
          |.g-doc h1 h2 h3 h4 h5 h6 {font-family: serif;}
          |.g-doc a { color: #6c003b !important;}
          |</style>
        """.stripMargin)
      css -> Html(body.outerHtml())
    }
  }

  override def get(key: String, noCache: Boolean = false): Option[Future[(Html, Html)]] = {
    app.configuration.getString(s"pages.external.google.$key").map { url =>
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
