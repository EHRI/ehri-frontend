package defines

import org.joda.time.DateTime
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormatter}
import play.api.mvc.QueryStringBindable

/**
 * The implicit values in this package allow the Play routes
 * to bind/unbind enumeration values, without those enums having
 * to be specifically aware of Play functionality.
 *
 * These values are imported into the generated routes files by
 * the build.
 */
package object binders {
  implicit val entityTypeBinder = EnumerationBinders.bindableEnum(EntityType)
  implicit val entityTypeQueryBinder = EnumerationBinders.queryStringBinder(EntityType)

  private val fullDateTimeFmt: DateTimeFormatter = ISODateTimeFormat.dateTime()
  private val partialDateTimeFmt: DateTimeFormatter = ISODateTimeFormat.date()

  implicit def dateTimeQueryBinder(implicit stringBinder: QueryStringBindable[String]) =
    new QueryStringBindable[DateTime] {
      def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, DateTime]] = {
        stringBinder.bind(key, params).collect {
          case Right(ds) if !ds.trim.isEmpty => try {
            Right(fullDateTimeFmt.parseDateTime(ds))
          } catch {
            case e: IllegalArgumentException => try {
              Right(partialDateTimeFmt.parseDateTime(ds))
            } catch {
              case e: IllegalArgumentException =>
                Left(s"Invalid date format: $ds")
            }
          }
        }
      }

      def unbind(key: String, value: DateTime): String = {
        stringBinder.unbind(key, fullDateTimeFmt.print(value))
      }
    }
}
