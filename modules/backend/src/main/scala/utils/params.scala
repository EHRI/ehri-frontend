package utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import services.rest.Constants._
import defines.{EntityType, EventType}
import play.api.mvc.QueryStringBindable
import utils.SystemEventParams.{Aggregation, ShowType}

object Ranged {
  def streamHeader: (String, String) = STREAM_HEADER_NAME -> true.toString
}

private[utils] trait NamespaceExtractor {
  protected def ns(key: String): String =
    if (key.contains("_")) key.substring(key.lastIndexOf("_") + 1) else ""

  protected def bindOr[T](key: String, params: Map[String, Seq[String]], or: T)(implicit b: QueryStringBindable[T]): T =
    b.bind(key, params).map(_.fold(err => or, v => v)).getOrElse(or)
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

  implicit def queryStringBindable = new QueryStringBindable[RangeParams] with NamespaceExtractor {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, RangeParams]] = {
      val namespace: String = ns(key)
      Some(Right(RangeParams(
        bindOr(namespace + OFFSET_PARAM, params, 0).max(0),
        bindOr(namespace + LIMIT_PARAM, params, DEFAULT_LIST_LIMIT).min(MAX_LIST_LIMIT)
      )))
    }

    override def unbind(key: String, params: RangeParams): String =
      utils.http.joinQueryString(params.toParams(ns(key)).distinct)
  }
}

case class RangeParams(offset: Int = 0, limit: Int = DEFAULT_LIST_LIMIT) extends Ranged {
  def withoutLimit: RangeParams = copy(limit = -1)

  def next: RangeParams = if (hasLimit) copy(offset + limit, limit) else this

  def prev: RangeParams = if (hasLimit) copy(0.max(offset - limit), limit) else this

  def toParams(ns: String = ""): Seq[(String, String)] = {
    val os = if (offset == 0) Seq.empty else Seq(offset.toString)
    val lm = if (limit == DEFAULT_LIST_LIMIT) Seq.empty else Seq(limit.toString)
    os.map(ns + OFFSET_PARAM -> _) ++ lm.map(ns + LIMIT_PARAM -> _)
  }
}


object PageParams {
  val PAGE_PARAM = "page"

  def empty: PageParams = PageParams()

  implicit def queryStringBindable: QueryStringBindable[PageParams] =
    new QueryStringBindable[PageParams] with NamespaceExtractor {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, PageParams]] = {
        val namespace: String = ns(key)
        Some(Right(PageParams(
          bindOr(namespace + PAGE_PARAM, params, 1).max(1),
          bindOr(namespace + LIMIT_PARAM, params, DEFAULT_LIST_LIMIT).min(MAX_LIST_LIMIT)
        )))
      }

      override def unbind(key: String, params: PageParams): String =
        utils.http.joinQueryString(params.toParams(ns(key)).distinct)
    }
}

/**
  * Class for handling page parameter data
  */
case class PageParams(page: Int = 1, limit: Int = DEFAULT_LIST_LIMIT) extends Ranged {
  def withoutLimit: PageParams = copy(limit = -1)

  def offset: Int = (page - 1) * limit.max(0)

  def next: PageParams = copy(page + 1)

  def prev: PageParams = copy(1.max(page - 1))

  def toParams(ns: String = ""): Seq[(String, String)] = {
    val pg = if (page == 1) Seq.empty else Seq(page.toString)
    val lm = if (limit == DEFAULT_LIST_LIMIT) Seq.empty else Seq(limit.toString)
    pg.map(ns + PageParams.PAGE_PARAM -> _) ++ lm.map(ns + LIMIT_PARAM -> _)
  }
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

  import defines.EnumUtils.enumMapping
  import defines.binders._
  import play.api.data.Form
  import play.api.data.Forms._
  import play.api.mvc.QueryStringBindable.bindableOption

  private implicit val dateBinder: QueryStringBindable[Option[LocalDateTime]]
  = bindableOption(dateTimeQueryBinder)

  private val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  def empty: SystemEventParams = new SystemEventParams()

  val SHOW = "show"

  object ShowType extends Enumeration {
    type Type = Value
    val All = Value("all")
    val Watched = Value("watched")
    val Follows = Value("follows")

    implicit val _bind: QueryStringBindable[ShowType.Value] = queryStringBinder(ShowType)
  }

  val AGGREGATION = "aggregation"

  object Aggregation extends Enumeration {
    type Type = Value
    val User = Value("user")
    val Strict = Value("strict")
    val Off = Value("off")

    implicit val _bind: QueryStringBindable[Aggregation.Value] = queryStringBinder(Aggregation)
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

  implicit def queryStringBindable: QueryStringBindable[SystemEventParams] =
    new QueryStringBindable[SystemEventParams] with NamespaceExtractor {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, SystemEventParams]] = {
        val namespace = ns(key)
        Some(Right(SystemEventParams(
          bindOr(namespace + USERS, params, Seq.empty[String]),
          bindOr(namespace + EVENT_TYPE, params, Seq.empty[EventType.Value])(
            tolerantSeqBinder(queryStringBinder(EventType))),
          bindOr(namespace + ITEM_TYPE, params, Seq.empty[EntityType.Value])(
            tolerantSeqBinder(queryStringBinder(EntityType))),
          bindOr(namespace + FROM, params, Option.empty[LocalDateTime])(dateBinder),
          bindOr(namespace + TO, params, Option.empty[LocalDateTime])(dateBinder),
          bindOr(namespace + SHOW, params, Option.empty[ShowType.Value]),
          bindOr(namespace + AGGREGATION, params, Option.empty[Aggregation.Value])
        )))
      }

      override def unbind(key: String, value: SystemEventParams): String =
        utils.http.joinQueryString(value.toSeq(ns(key)))
    }
}