package backend

import backend.rest.ItemNotFound
import play.api.i18n.Lang
import play.twirl.api.Html

import scala.concurrent.Future

/**
 * Mock external pages.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class MockHtmlPages() extends HtmlPages {
  override def get(key: String, noCache: Boolean)(implicit lang: Lang): Option[Future[(Html, Html)]] = {
    mocks.externalPages.get(key).map { html =>
      Some(Future.successful(Html("") -> html))
    }.getOrElse(throw new ItemNotFound())
  }
}
