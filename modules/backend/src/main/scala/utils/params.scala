package utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import services.data.Constants._
import defines.{EntityType, EventType}
import utils.SystemEventParams.{Aggregation, ShowType}


object Ranged {
  def streamHeader: (String, String) = STREAM_HEADER_NAME -> true.toString
}

trait Ranged {
  def offset: Int

  def limit: Int

  def hasLimit: Boolean = limit >= 0

  def queryParams: Seq[(String, String)] =
    Seq(OFFSET_PARAM -> offset.toString, LIMIT_PARAM -> limit.toString)

  def headers: Seq[(String, String)] =
    if (hasLimit) Seq.empty else Seq(Ranged.streamHeader)
}

object RangeParams {
  def empty: RangeParams = RangeParams()
}

case class RangeParams(offset: Int = 0, limit: Int = DEFAULT_LIST_LIMIT) extends Ranged {
  def withoutLimit: RangeParams = copy(limit = -1)

  def next: RangeParams = if (hasLimit) copy(offset + limit, limit) else this

  def prev: RangeParams = if (hasLimit) copy(0.max(offset - limit), limit) else this
}


object PageParams {
  def empty: PageParams = PageParams()
}

/**
  * Class for handling page parameter data
  */
case class PageParams(page: Int = 1, limit: Int = DEFAULT_LIST_LIMIT) extends Ranged {
  def withoutLimit: PageParams = copy(limit = -1)

  def offset: Int = (page - 1) * limit.max(0)

  def next: PageParams = copy(page + 1)

  def prev: PageParams = copy(1.max(page - 1))
}


case class SystemEventParams(
  users: Seq[String] = Nil,
  eventTypes: Seq[EventType.Value] = Nil,
  itemTypes: Seq[EntityType.Value] = Nil,
  from: Option[LocalDateTime] = None,
  to: Option[LocalDateTime] = None,
  show: Option[ShowType.Value] = None,
  aggregation: Option[Aggregation.Value] = None) {

  import utils.SystemEventParams._

  def toSeq(ns: String = ""): Seq[(String, String)] = users
    .filterNot(_.isEmpty).map(u => ns + USERS -> u) ++
    eventTypes.map(et => ns + EVENT_TYPE -> et.toString) ++
    itemTypes.map(et => ns + ITEM_TYPE -> et.toString) ++
    from.map(f => ns + FROM -> fmt.format(f)).toSeq ++
    to.map(t => ns + TO -> fmt.format(t)).toSeq ++
    show.map(f => ns + SHOW -> f.toString).toSeq ++
    aggregation.map(f => ns + AGGREGATION -> f.toString)
}

object SystemEventParams {

  import EnumUtils.enumMapping
  import play.api.data.Form
  import play.api.data.Forms._

  private val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  def empty: SystemEventParams = new SystemEventParams()

  object ShowType extends Enumeration {
    type Type = Value
    val All = Value("all")
    val Watched = Value("watched")
    val Follows = Value("follows")
  }

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
      FROM -> optional(localDateTime(pattern = DATE_PATTERN)),
      TO -> optional(localDateTime(pattern = DATE_PATTERN)),
      SHOW -> optional(enumMapping(ShowType)),
      AGGREGATION -> optional(enumMapping(Aggregation))
    )(SystemEventParams.apply)(SystemEventParams.unapply)
  )
}