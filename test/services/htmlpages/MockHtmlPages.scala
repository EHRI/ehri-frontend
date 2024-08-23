package services.htmlpages

import play.api.i18n.Messages
import play.twirl.api.Html
import services.data.ItemNotFound

import scala.concurrent.Future

/**
 * Mock external pages.
 */
case class MockHtmlPages() extends HtmlPages {
  override def get(key: String, noCache: Boolean)(implicit messages: Messages): Option[Future[(String, Html, Html)]] = {
    mockdata.externalPages.get(key).map { data =>
      Some(Future.successful(data))
    }.getOrElse(throw new ItemNotFound())
  }
}
