package utils

import defines.{EntityType, EventType}
import play.api.mvc.QueryStringBindable
import services.data.Constants._
import utils.binders._

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

case class SystemEventParams(
  users: Seq[String] = Nil,
  eventTypes: Seq[EventType.Value] = Nil,
  itemTypes: Seq[EntityType.Value] = Nil,
  from: Option[LocalDateTime] = None,
  to: Option[LocalDateTime] = None,
  show: Option[SystemEventParams.ShowType.Value] = None,
  aggregation: Option[SystemEventParams.Aggregation.Value] = None) {

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

  implicit val _queryBinder: QueryStringBindable[SystemEventParams] =
    new QueryStringBindable[SystemEventParams] with NamespaceExtractor {

      private implicit val aggBinder: QueryStringBindable[SystemEventParams.Aggregation.Value] = queryStringBinder(Aggregation)
      private implicit val showBinder: QueryStringBindable[SystemEventParams.ShowType.Value] = queryStringBinder(ShowType)

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, SystemEventParams]] = {
        val namespace = ns(key)
        Some(Right(SystemEventParams(
          bindOr(namespace + USERS, params, Seq.empty[String]),
          bindOr(namespace + EVENT_TYPE, params, Seq.empty[EventType.Value])(
            tolerantSeqBinder(queryStringBinder(EventType))),
          bindOr(namespace + ITEM_TYPE, params, Seq.empty[EntityType.Value])(
            tolerantSeqBinder(queryStringBinder(EntityType))),
          bindOr(namespace + FROM, params, Option.empty[LocalDateTime])(optionalDateTimeQueryBinder),
          bindOr(namespace + TO, params, Option.empty[LocalDateTime])(optionalDateTimeQueryBinder),
          bindOr(namespace + SHOW, params, Option.empty[ShowType.Value]),
          bindOr(namespace + AGGREGATION, params, Option.empty[Aggregation.Value])
        )))
      }

      override def unbind(key: String, value: SystemEventParams): String =
        utils.http.joinQueryString(value.toSeq(ns(key)))
    }
}



