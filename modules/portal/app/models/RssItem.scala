package models

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.twirl.api.Html

import scala.util.control.Exception._

case class RssFeed(
  title: String,
  url: String,
  description: Option[String],
  items: Seq[RssItem]
)

case class RssItem(
  title: String,
  link: String,
  description: Html,
  pubDate: Option[DateTime] = None
)
object RssFeed {
  private val pat = DateTimeFormat.forPattern("EEE, dd MMM yyyy H:m:s Z")

  def apply(feedXml: String, numEntries: Int = 5): RssFeed = {
    val elem = scala.xml.XML.loadString(feedXml) \ "channel"
    new RssFeed(
      (elem \ "title").text,
      (elem \ "link").text,
      if ((elem \ "description").text.trim.isEmpty) None
        else Some((elem \ "description").text),
      (elem \ "item").take(numEntries).map { item =>
        new RssItem(
          title = (item \ "title").text,
          link = (item \ "link").text,
          description = Html((item \ "description").text),
          pubDate = allCatch.opt(DateTime.parse((item \ "pubDate").text, pat))
        )
      }
    )
  }
}

