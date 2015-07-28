package utils

import play.api.mvc.RequestHeader
import play.api.data.Form
import play.api.data.Forms._
import backend.rest.Constants._
import defines.{EnumUtils, EntityType, EventType}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import utils.SystemEventParams.{Aggregation, ShowType}

object Ranged {
  def streamHeader: (String, String) = STREAM_HEADER_NAME -> true.toString
}

trait Ranged {
  def offset: Int
  def limit: Int

  def hasLimit = limit >= 0

  def queryParams: Seq[(String,String)] =
    Seq(OFFSET_PARAM -> offset.toString,  LIMIT_PARAM -> limit.toString)

  def headers: Seq[(String,String)] =
    if (hasLimit) Seq.empty else Seq(Ranged.streamHeader)
}

object RangeParams {
  def empty: RangeParams = new RangeParams()

  def fromRequest(request: RequestHeader, namespace: String = ""): RangeParams = {
    Form(
      mapping(
        namespace + OFFSET_PARAM -> default(number(min = 0), 0),
        namespace + LIMIT_PARAM -> default(number(min = -1, max = MAX_LIST_LIMIT), DEFAULT_LIST_LIMIT)
      )(RangeParams.apply)(RangeParams.unapply)
    ).bindFromRequest(request.queryString).value.getOrElse(empty)
  }
}

case class RangeParams(offset: Int = 0, limit: Int = DEFAULT_LIST_LIMIT) extends Ranged {
  def withoutLimit = copy(limit = -1)
}


object PageParams {

  val PAGE_PARAM = "page"

  def empty: PageParams = new PageParams

  def fromRequest(request: RequestHeader, namespace: String = ""): PageParams = {
    Form(
      mapping(
        namespace + PAGE_PARAM -> default(number(min = 1), 1),
        namespace + LIMIT_PARAM -> default(number(min = 1, max = MAX_LIST_LIMIT), DEFAULT_LIST_LIMIT)
      )(PageParams.apply)(PageParams.unapply)
    ).bindFromRequest(request.queryString).value.getOrElse(empty)
  }
}

/**
 * Class for handling page parameter data
 */
case class PageParams(page: Int = 1, limit: Int = DEFAULT_LIST_LIMIT) extends Ranged {
  def withoutLimit = copy(limit = -1)
  def offset: Int = (page - 1) * limit.max(0)
}


case class SystemEventParams(
  users: Seq[String] = Nil,
  eventTypes: Seq[EventType.Value] = Nil,
  itemTypes: Seq[EntityType.Value] = Nil,
  from: Option[DateTime] = None,
  to: Option[DateTime] = None,
  show: Option[ShowType.Value] = None,
  aggregation: Option[Aggregation.Value] = None) {
  import SystemEventParams._
  private val fmt = ISODateTimeFormat.dateTime.withZoneUTC

  def toSeq: Seq[(String,String)] = {
    (users.filterNot(_.isEmpty).map(u => USERS -> u) ++
      eventTypes.map(et => EVENT_TYPE -> et.toString) ++
      itemTypes.map(et => ITEM_TYPE -> et.toString) ++
      from.map(f => FROM -> fmt.print(f)).toSeq ++
      to.map(t => TO -> fmt.print(t)).toSeq ++
      show.map(f => SHOW -> f.toString).toSeq ++
      aggregation.map(f => AGGREGATION -> f.toString).toSeq
      ).toSeq
  }
}

object SystemEventParams {

  import EnumUtils.enumMapping

  def empty: SystemEventParams = new SystemEventParams()

  val SHOW = "show"
  object ShowType extends Enumeration {
    type Type = Value
    val All = Value("all")
    val Watched = Value("watched")
    val Follows = Value("follows")
  }

  val AGGREGATION = "aggregation"
  object Aggregation extends Enumeration {
    type Type = Value
    val User = Value("user")
    val Strict = Value("strict")
    val Off = Value("off")
  }

  def form: Form[SystemEventParams] = Form(
    mapping(
      USERS -> seq(text),
      EVENT_TYPE -> seq(enumMapping(EventType)),
      ITEM_TYPE -> seq(enumMapping(EntityType)),
      FROM -> optional(jodaDate(pattern = DATE_PATTERN)),
      TO -> optional(jodaDate(pattern = DATE_PATTERN)),
      SHOW -> optional(enumMapping(ShowType)),
      AGGREGATION -> optional(enumMapping(Aggregation))
    )(SystemEventParams.apply)(SystemEventParams.unapply)
  )

  def fromRequest(request: RequestHeader): SystemEventParams =
    form.bindFromRequest(request.queryString).value.getOrElse(SystemEventParams.empty)
}