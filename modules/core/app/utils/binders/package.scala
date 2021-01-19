package utils

import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{LocalDate, LocalDateTime, YearMonth}
import defines.{ContentTypes, EntityType, EventType}
import play.api.mvc.QueryStringBindable.bindableOption
import play.api.mvc.{PathBindable, QueryStringBindable}
import services.data.Constants
import services.search._
import utils.SystemEventParams.{Aggregation, ShowType}

import scala.annotation.tailrec

/**
  * The implicit values in this package allow the Play routes
  * to bind/unbind enumeration values, without those enums having
  * to be specifically aware of Play functionality.
  *
  * These values are imported into the generated routes files by
  * the build.
  */
package object binders {

  import services.data.Constants._

  def bindableEnum[E <: Enumeration](enum: E): PathBindable[E#Value] = new PathBindable[E#Value] {
    def bind(key: String, value: String): Either[String, enum.Value] =
      enum.values.find(_.toString.toLowerCase == value.toLowerCase) match {
        case Some(v) => Right(v)
        case None => Left(s"Unknown url path segment '$value'")
      }

    def unbind(key: String, value: E#Value): String = value.toString.toLowerCase
  }

  def queryStringBinder[E <: Enumeration](enum: E)(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[E#Value] =
    new QueryStringBindable[E#Value] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, E#Value]] =
        for (v <- stringBinder.bind(key, params)) yield v match {
          case Right(p) => enum.values.find(_.toString.toLowerCase == p.toLowerCase) match {
            case Some(ev) => Right(ev)
            case None => Left(s"Unable to bind a valid value from '$p' alternatives: ${enum.values}")
          }
          case _ => Left(s"Unable to bind a valid value from alternatives: ${enum.values}")
        }

      override def unbind(key: String, value: E#Value): String = stringBinder.unbind(key, value.toString)
    }

  implicit val entityTypePathBinder: PathBindable[EntityType.Value] = bindableEnum(EntityType)

  implicit val entityTypeQueryBinder: QueryStringBindable[EntityType.Value] = queryStringBinder(EntityType)

  implicit val contentTypePathBinder: PathBindable[ContentTypes.Value] = bindableEnum(ContentTypes)

  implicit val contentTypeQueryBinder: QueryStringBindable[ContentTypes.Value] = queryStringBinder(ContentTypes)

  private val fullDateTimeFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  implicit def dateTimeQueryBinder(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[LocalDateTime] =
    new QueryStringBindable[LocalDateTime] {
      def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, LocalDateTime]] = {
        stringBinder.bind(key, params).collect {
          case Right(ds) if ds.trim.nonEmpty => try {
            Right(LocalDateTime.parse(ds))
          } catch {
            case e: DateTimeParseException => try {
              Right(LocalDate.parse(ds).atStartOfDay())
            } catch {
              case e: DateTimeParseException => try {
                Right(YearMonth.parse(ds).atDay(1).atStartOfDay())
              } catch {
                case e: DateTimeParseException =>
                  Left(s"Invalid date format: $ds")
              }
            }
          }
        }
      }

      def unbind(key: String, value: LocalDateTime): String = {
        stringBinder.unbind(key, fullDateTimeFmt.format(value))
      }
    }

  implicit def optionalDateTimeQueryBinder: QueryStringBindable[Option[LocalDateTime]] =
    bindableOption(dateTimeQueryBinder)

  def tolerantSeqBinder[T](implicit qbs: QueryStringBindable[T]): QueryStringBindable[Seq[T]] =
    new QueryStringBindable[Seq[T]] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Seq[T]]] = {
        @tailrec
        def collectResults(values: List[String], results: List[T]): Either[String, Seq[T]] = {
          values match {
            case Nil => Right(results.reverse) // to preserve the original order
            case head :: rest => qbs.bind(key, Map(key -> Seq(head))) match {
              case None => collectResults(rest, results)
              case Some(Right(result)) => collectResults(rest, result :: results)
              // Ignore errors
              case Some(Left(err)) => collectResults(rest, results)
            }
          }
        }

        params.get(key) match {
          case None => Some(Right(Nil))
          case Some(values) => Some(collectResults(values.toList, Nil))
        }
      }

      override def unbind(key: String, value: Seq[T]): String =
        utils.http.joinQueryString(value.map(v => key -> qbs.unbind(key, v)))
    }


  implicit def pageParamsQueryBinder: QueryStringBindable[PageParams] =
    new QueryStringBindable[PageParams] with NamespaceExtractor {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, PageParams]] = {
        val namespace: String = ns(key)
        Some(Right(PageParams(
          bindOr(namespace + PAGE_PARAM, params, 1).max(1),
          bindOr(namespace + LIMIT_PARAM, params, DEFAULT_LIST_LIMIT).min(MAX_LIST_LIMIT)
        )))
      }

      override def unbind(key: String, params: PageParams): String =
        utils.http.joinQueryString(toParams(params, ns(key)).distinct)


      private def toParams(p: PageParams, ns: String = ""): Seq[(String, String)] = {
        val pg = if (p.page == 1) Seq.empty else Seq(p.page.toString)
        val lm = if (p.limit == DEFAULT_LIST_LIMIT) Seq.empty else Seq(p.limit.toString)
        pg.map(ns + PAGE_PARAM -> _) ++ lm.map(ns + Constants.LIMIT_PARAM -> _)
      }
    }


  implicit def rangeParamsQueryBinder: QueryStringBindable[RangeParams] =
    new QueryStringBindable[RangeParams] with NamespaceExtractor {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, RangeParams]] = {
        val namespace: String = ns(key)
        Some(Right(RangeParams(
          bindOr(namespace + OFFSET_PARAM, params, 0).max(0),
          bindOr(namespace + LIMIT_PARAM, params, DEFAULT_LIST_LIMIT).min(MAX_LIST_LIMIT)
        )))
      }

      override def unbind(key: String, params: RangeParams): String =
        utils.http.joinQueryString(toParams(params, ns(key)).distinct)

      private def toParams(p: RangeParams, ns: String = ""): Seq[(String, String)] = {
        val os = if (p.offset == 0) Seq.empty else Seq(p.offset.toString)
        val lm = if (p.limit == DEFAULT_LIST_LIMIT) Seq.empty else Seq(p.limit.toString)
        os.map(ns + OFFSET_PARAM -> _) ++ lm.map(ns + LIMIT_PARAM -> _)
      }
    }

  implicit def systemEventParamsQueryBinder: QueryStringBindable[SystemEventParams] =
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

  implicit def fieldFilterQueryBinder: QueryStringBindable[Seq[FieldFilter]] with NamespaceExtractor =
    new QueryStringBindable[Seq[FieldFilter]] with NamespaceExtractor {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Seq[FieldFilter]]] = {
        def parse(key: String, value: Seq[String]): Option[Either[String, FieldFilter]] = {
          if (key.startsWith(s"${FieldFilter.FIELDS}[") && key.endsWith("]")) {
            val et = key.substring(7, key.length - 1)
            val fields = value.flatMap(_.split(",")).filter(_.trim.nonEmpty)
            try Some(Right(FieldFilter(EntityType.withName(et), fields))) catch {
              case e: Throwable => Some(Left(e.getMessage))
            }
          } else None
        }

        val filters = params.foldLeft(Seq.empty[FieldFilter]) { case (s, (k, v)) =>
          parse(k, v) match {
            case Some(Right(f)) => f +: s
            case _ => s
          }
        }
        Some(Right(filters))
      }

      override def unbind(key: String, value: Seq[FieldFilter]): String =
        utils.http.joinQueryString(value.flatMap(_.toSeq(ns(key))))
    }

  implicit def bboxQueryBinder(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[BoundingBox] =
    new QueryStringBindable[BoundingBox] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, BoundingBox]] =
        params.get(key).flatMap(_.headOption).map(BoundingBox.fromString)

      override def unbind(key: String, value: BoundingBox): String = stringBinder.unbind(key, value.toString)
    }

  implicit def latLngQueryBinder(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[LatLng] =
    new QueryStringBindable[LatLng] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, LatLng]] =
        params.get(key).flatMap(_.headOption).map(LatLng.fromString)

      override def unbind(key: String, value: LatLng): String = stringBinder.unbind(key, value.toString)
    }

  implicit def searchParamsQueryBinder(
    implicit seqStrBinder: QueryStringBindable[Seq[String]]): QueryStringBindable[SearchParams] with NamespaceExtractor = new QueryStringBindable[SearchParams] with NamespaceExtractor {
    import services.search.SearchParams._

    private implicit val sortBinder = utils.binders.queryStringBinder(SearchSort)

    // Backwards compatibility for old Solr-based `sort parameters. We fall
    // back to these if none of the new sort values are valid.
    def bindOldSort(key: String, params: Map[String, Seq[String]]): Option[SearchSort.Value] = {
      val oldSortMap: Map[String, SearchSort.Value] = Map(
        "identifier.asc" -> SearchSort.Id,
        "isParent.desc,identifier.asc" -> SearchSort.Id,
        "score.desc" -> SearchSort.Score,
        "name_sort.asc" -> SearchSort.Name,
        "lastUpdated.desc" -> SearchSort.DateNewest,
        "countryCode.asc" -> SearchSort.Country,
        "repositoryName.asc" -> SearchSort.Holder,
        "geodist().asc" -> SearchSort.Location,
        "charCount.desc" -> SearchSort.Detail,
        "childCount.desc" -> SearchSort.ChildCount
      )
      params
        .get(key)
        .map(_.flatMap(oldSortMap.get).headOption)
        .getOrElse(Option.empty)
    }


    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, SearchParams]] = {
      val namespace: String = ns(key)
      val rawParams = SearchParams(
        bindOr(namespace + QUERY, params, Option.empty[String]).filter(_.trim.nonEmpty),
        bindOr(namespace + SORT, params, bindOldSort(namespace + SORT, params)),
        bindOr(namespace + ENTITY, params, Seq.empty[EntityType.Value])(tolerantSeqBinder(queryStringBinder(EntityType))),
        bindOr(namespace + FIELD, params, Seq.empty[SearchField.Value])(tolerantSeqBinder(queryStringBinder(SearchField))),
        bindOr(namespace + FACET, params, Seq.empty[String]),
        bindOr(namespace + EXCLUDE, params, Seq.empty[String]),
        bindOr(namespace + FILTERS, params, Seq.empty[String]),
        bindOr(namespace + BBOX, params, Option.empty[BoundingBox]),
        bindOr(namespace + LATLNG, params, Option.empty[LatLng])
      )
      // NB: Sorting by location is invalid without a valid `latlng` parameter
      val checked = if(rawParams.sort.contains(SearchSort.Location) && rawParams.latLng.isEmpty)
        rawParams.copy(sort = None)
      else rawParams

      Some(Right(checked))
    }

    override def unbind(key: String, params: SearchParams): String =
      utils.http.joinQueryString(toParams(params, ns(key)).distinct)

    private def toParams(p: SearchParams, ns: String = ""): Seq[(String, String)] = {
      p.query.map(q => ns + QUERY -> q).toSeq ++
        p.sort.map(s => ns + SORT -> s.toString).toSeq ++
        p.entities.map(e => ns + ENTITY -> e.toString) ++
        p.fields.map(f => ns + FIELD -> f.toString) ++
        p.facets.map(f => ns + FACET -> f) ++
        p.excludes.map(e => ns + EXCLUDE -> e) ++
        p.filters.map(f => ns + FILTERS -> f) ++
        p.bbox.map { box => ns + BBOX -> box.toString}.toSeq ++
        p.latLng.map { latLng => ns + LATLNG -> latLng.toString}.toSeq
    }
  }
}

