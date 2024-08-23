package services.htmlpages

import play.api.i18n.Messages
import play.twirl.api.Html

import scala.concurrent.Future

/**
  * Interface for static HTML pages that are pulled
  * from some external source and embedded in our
  * page template.
  */
trait HtmlPages {
  /**
    * Fetch an external page fragment with a given key.
    *
    * @param key      the identifier for this fragment
    * @param noCache  whether to prevent the fragment coming from the cache
    * @param messages an implicit messages instance containing the current language
    * @return An optional, future of (Title, Css, Body HTML)
    */
  def get(key: String, noCache: Boolean = false)(implicit messages: Messages): Option[Future[(String, Html, Html)]]
}
