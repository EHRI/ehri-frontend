package defines

import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{LocalDate, LocalDateTime, YearMonth}

import play.api.mvc.{PathBindable, QueryStringBindable}

/**
  * The implicit values in this package allow the Play routes
  * to bind/unbind enumeration values, without those enums having
  * to be specifically aware of Play functionality.
  *
  * These values are imported into the generated routes files by
  * the build.
  */
package object binders {
  implicit val entityTypeBinder: PathBindable[EntityType.Value] =
    EnumerationBinders.bindableEnum(EntityType)
  implicit val entityTypeQueryBinder: QueryStringBindable[EntityType.Value] =
    EnumerationBinders.queryStringBinder(EntityType)

  private val fullDateTimeFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  implicit def dateTimeQueryBinder(implicit stringBinder: QueryStringBindable[String]) =
    new QueryStringBindable[LocalDateTime] {
      def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, LocalDateTime]] = {
        stringBinder.bind(key, params).collect {
          case Right(ds) if !ds.trim.isEmpty => try {
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
}
