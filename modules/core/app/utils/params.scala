package utils

import play.api.mvc.RequestHeader
import play.api.data.Form
import play.api.data.Forms._
import backend.rest.Constants._
import defines.{EntityType, EventType}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import utils.SystemEventParams.ShowType


object PageParams {

  def streamHeader: (String, String) = STREAM_HEADER -> true.toString

  def empty: PageParams = new PageParams

  def fromRequest(request: RequestHeader, namespace: String = ""): PageParams = {
    Form(
      mapping(
        namespace + PAGE_PARAM -> default(number, 1),
        namespace + COUNT_PARAM -> default(number, DEFAULT_LIST_LIMIT)
      )(PageParams.apply)(PageParams.unapply)
    ).bindFromRequest(request.queryString).value.getOrElse(empty)
  }
}

/**
 * Class for handling page parameter data
 */
case class PageParams(page: Int = 1, count: Int = DEFAULT_LIST_LIMIT) {
  def withoutLimit = copy(count = -1)
  def hasLimit = count < 0
  def offset: Int = (page - 1) * count

  def queryParams: Seq[(String,String)] =
    Seq(PAGE_PARAM -> page.toString,  COUNT_PARAM -> count.toString)

  def headers: Seq[(String,String)] =
    if (hasLimit) Seq.empty else Seq(PageParams.streamHeader)
}


case class SystemEventParams(
  users: List[String] = Nil,
  eventTypes: List[EventType.Value] = Nil,
  itemTypes: List[EntityType.Value] = Nil,
  from: Option[DateTime] = None,
  to: Option[DateTime] = None,
  show: Option[ShowType.Value] = None) {
  import SystemEventParams._
  private val fmt = ISODateTimeFormat.dateTime.withZoneUTC

  def toSeq: Seq[(String,String)] = {
    (users.filterNot(_.isEmpty).map(u => USERS -> u) :::
      eventTypes.map(et => EVENT_TYPE -> et.toString) :::
      itemTypes.map(et => ITEM_TYPE -> et.toString) :::
      from.map(f => FROM -> fmt.print(f)).toList :::
      to.map(t => TO -> fmt.print(t)).toList :::
      show.map(f => SHOW -> f.toString).toList).toSeq
  }
}

object SystemEventParams {

  def empty: SystemEventParams = new SystemEventParams()

  val SHOW = "show"
  object ShowType extends Enumeration {
    type Type = Value
    val All = Value("all")
    val Watched = Value("watched")
    val Follows = Value("follows")
  }

  def form: Form[SystemEventParams] = Form(
    mapping(
      USERS -> list(text),
      EVENT_TYPE -> list(models.forms.enum(EventType)),
      ITEM_TYPE -> list(models.forms.enum(EntityType)),
      FROM -> optional(jodaDate(pattern = DATE_PATTERN)),
      TO -> optional(jodaDate(pattern = DATE_PATTERN)),
      SHOW -> optional(models.forms.enum(ShowType))
    )(SystemEventParams.apply)(SystemEventParams.unapply)
  )

  def fromRequest(request: RequestHeader): SystemEventParams = {
    form.bindFromRequest(request.queryString).value.getOrElse(new SystemEventParams())
  }
}