package utils

import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}
import play.api.i18n.Messages
import play.api.mvc.RequestHeader

/**
 * Utils for converting URL-friendly date facet params
 * to Solr format.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
object DateFacetUtils {

  import play.api.data.Form
  import play.api.data.Forms._

  val DATE_PARAM = "dates"

  val dateQueryForm = Form(single(DATE_PARAM -> nonEmptyText))

  val formatter: DateTimeFormatter
      = ISODateTimeFormat.dateTime().withZoneUTC()

  private def startDate(year: Int): String = formatter.print(new DateTime(year, 1, 1, 0, 0))
  private def endDate(year: Int): String = formatter.print(new DateTime(year, 12, 12, 23, 59))
  private val dateParamValueMatcher = new scala.util.matching.Regex("""^(\d{4})?_(\d{4})?$""", "start", "end")

  /**
   * Convert the format to a Solr query.
   */
  def formatAsSolrQuery(ds: String): String = {
    dateParamValueMatcher.findFirstMatchIn(ds).map { m =>
      val start = Option(m.group("start")).map(_.toInt)
      val end = Option(m.group("end")).map(_.toInt)
      (start, end) match {
        case (Some(s), Some(e)) if s == e => s"[${startDate(s)} TO ${endDate(e)}]"
        case (Some(s), Some(e)) if s <= e => s"[${startDate(s)} TO ${endDate(e)}]"
        case (Some(s), Some(e)) if s > e => s"[${startDate(e)} TO ${endDate(s)}]"
        case (Some(s), None) => s"[${startDate(s)} TO *]"
        case (None, Some(e)) => s"[* TO ${endDate(e)}]"
        case _ => "*"
      }
    }.getOrElse ("*")
  }

  /**
   * Get a formatted representation of the date string.
   */
  def formatReadable(ds: String)(implicit request: RequestHeader): Option[String] = {
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
