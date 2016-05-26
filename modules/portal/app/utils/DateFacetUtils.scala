package utils

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}
import play.api.i18n.{MessagesApi, Messages}
import utils.search._

/**
 * Utils for converting URL-friendly date facet params
 * to Solr format.
 */
@Singleton
case class DateFacetUtils @Inject()(implicit messagesApi: MessagesApi) extends play.api.i18n.I18nSupport {

  import DateFacetUtils._

  private val dateParamValueMatcher = new scala.util.matching.Regex("""^(\d{4})?-(\d{4})?$""", "start", "end")

  /**
   * Convert the format to a Solr query.
   */
  def formatAsQuery(ds: String): QueryPoint = {
    dateParamValueMatcher.findFirstMatchIn(ds).map { m =>
      val start = Option(m.group("start")).map(_.toInt)
      val end = Option(m.group("end")).map(_.toInt)
      (start, end) match {
        case (Some(s), Some(e)) if s == e => Val(startDate(s)) to Val(endDate(e))
        case (Some(s), Some(e)) if s <= e => Val(startDate(s)) to Val(endDate(e))
        case (Some(s), Some(e)) if s > e => Val(startDate(e)) to Val(endDate(s))
        case (Some(s), None) => Val(startDate(s)) to End
        case (None, Some(e)) => Start to Val(endDate(e))
        case _ => Start
      }
    }.getOrElse(Start)
  }

  /**
   * Get a formatted representation of the date string.
   */
  def formatReadable(ds: String): Option[String] = {
    dateParamValueMatcher.findFirstMatchIn(ds).map { m =>
      val start = Option(m.group("start")).map(_.toInt)
      val end = Option(m.group("end")).map(_.toInt)
      (start, end) match {
        case (Some(s), Some(e)) if s == e => Messages(DATE_PARAM + ".exact", s)
        case (Some(s), Some(e)) if s <= e => Messages(DATE_PARAM + ".between", s, e)
        case (Some(s), Some(e)) if s > e => Messages(DATE_PARAM + ".between", e, s)
        case (Some(s), None) => Messages(DATE_PARAM + ".after", s)
        case (None, Some(e)) => Messages(DATE_PARAM + ".before", e)
        case _ => Messages(DATE_PARAM + ".all")
      }
    }
  }
}

object DateFacetUtils {
  import play.api.data.Form
  import play.api.data.Forms._

  val DATE_PARAM = "dates"

  val dateQueryForm = Form(single(DATE_PARAM -> nonEmptyText))

  def startDate(year: Int): String = formatter.print(new DateTime(year, 1, 1, 0, 0))
  def endDate(year: Int): String = formatter.print(new DateTime(year, 12, 12, 23, 59))

  val formatter: DateTimeFormatter = ISODateTimeFormat.dateTime().withZoneUTC()
}
