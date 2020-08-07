package utils

import play.api.mvc.QueryStringBindable.bindableOption
import play.api.mvc.{PathBindable, QueryStringBindable}

import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{LocalDate, LocalDateTime, YearMonth}
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

  def bindableEnum[E <: Enumeration](`enum`: E): PathBindable[E#Value] = new PathBindable[E#Value] {
    def bind(key: String, value: String): Either[String, enum.Value] =
      enum.values.find(_.toString.toLowerCase == value.toLowerCase) match {
        case Some(v) => Right(v)
        case None => Left(s"Unknown url path segment '$value'")
      }

    def unbind(key: String, value: E#Value): String = value.toString.toLowerCase
  }

  def queryStringBinder[E <: Enumeration](`enum`: E)(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[E#Value] =
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

  implicit val optionalDateTimeQueryBinder: QueryStringBindable[Option[LocalDateTime]] =
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
}
