package backend

import backend.rest.ItemNotFound
import play.api.i18n.Messages
import play.twirl.api.Html

import scala.concurrent.Future

/**
 * Mock external pages.
 */
case class MockHtmlPages() extends HtmlPages {
  override def get(key: String, noCache: Boolean)(implicit messages: Messages): Option[Future[(Html, Html)]] = {
    mockdata.externalPages.get(key).map { html =>
      Some(Future.successful(Html("") -> html))
    }.getOrElse(throw new ItemNotFound())
  }
}
