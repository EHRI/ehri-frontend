package models

import play.api.libs.json._
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsObject
import play.api.data.validation.ValidationError

/**
 * User: michaelb
 */
package object json {

  /**
   * Extensions to JsPath
   * @param path The extended path object
   */
  implicit class JsPathExtensions(path: JsPath) {
    /**
     * Read a value only if it's equal to the given value.
     */
    def readIfEquals[T](t: T)(implicit r: Reads[T]): Reads[T] =
      path.read[T](Reads.filter[T](ValidationError("validate.error.incorrectType", t))(_ == t))

    def formatIfEquals[T](t: T)(implicit f: Format[T]): OFormat[T] = path.format[T](readIfEquals(t))

    def nullableListReads[T](implicit fmt: Reads[T]): Reads[List[T]] = new Reads[List[T]] {
      def reads(json: JsValue): JsResult[List[T]] = {
        path.asSingleJsResult(json).fold(
          invalid = { err =>
            JsSuccess[List[T]](List.empty[T], path)
          },
          valid = { v =>
            v.validate[List[T]](Reads.list(fmt))
          }
        )
      }
    }

    def nullableListWrites[T](implicit fmt: Writes[T]): OWrites[List[T]] = {
      new OWrites[List[T]] {
        def writes(o: List[T]): JsObject
        = if (o.isEmpty) Json.obj()
        else path.write[List[T]].writes(o)
      }
    }

    def nullableListFormat[T](implicit fmt: Format[T]): OFormat[List[T]] =
      OFormat[List[T]](nullableListReads(fmt), nullableListWrites(fmt))

    def lazyNullableListReads[T](fmt: => Reads[T]): Reads[List[T]] =
      Reads(js => nullableListReads(fmt).reads(js))

    def lazyNullableListWrites[T](fmt: => Writes[T]): OWrites[List[T]] =
      OWrites((t: List[T]) => nullableListWrites[T](fmt).writes(t).as[JsObject])

    def lazyNullableListFormat[T](fmt: => Format[T]): OFormat[List[T]] =
      OFormat[List[T]](lazyNullableListReads(fmt), lazyNullableListWrites(fmt))
  }
}
