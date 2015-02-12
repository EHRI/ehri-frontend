package models

import play.api.libs.json._
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsObject
import play.api.data.validation.ValidationError

package object json {

  /** Extensions to [[play.api.libs.json.JsPath]].
   *
   * @param path The extended path object
   */
  implicit class JsPathExtensions(path: JsPath) {

    /** Read a value only if it's equal to the given value. */
    def readIfEquals[T](t: T)(implicit r: Reads[T]): Reads[T] =
      path.read[T](Reads.filter[T](ValidationError("validate.error.incorrectType", t))(_ == t))

    /** Attempt to read a list of T, falling back to a single T. */
    def readListOrSingle[T](implicit r: Reads[T]): Reads[List[T]] =
      path.read[List[T]].orElse(path.readNullable[T].map(_.toList))

    /** Lazy variant of [[models.json.JsPathExtensions.readListOrSingle]]. */
    def lazyReadListOrSingle[T](r: => Reads[T]): Reads[List[T]] =
      Reads(js => readListOrSingle(r).reads(js))

    /** Nullable variant of [[models.json.JsPathExtensions.readListOrSingle]]. */
    def readListOrSingleNullable[T](implicit r: Reads[T]): Reads[Option[List[T]]] =
      path.readNullable[List[T]].orElse(path.readNullable[T].map(s => s.map(List(_))))

    /** Lazy variant of [[models.json.JsPathExtensions.readListOrSingleNullable]]. */
    def lazyReadListOrSingleNullable[T](r: => Reads[T]): Reads[Option[List[T]]] =
      Reads(js => readListOrSingleNullable(r).reads(js))

    /** Attempt to read a path, falling back on a default value. */
    def readWithDefault[T](t: T)(implicit r: Reads[T]): Reads[T] =
      path.read[T].orElse(Reads.pure(t))

    /** Attempt to read a list, falling back on an empty list if the
     * path does not exist.
     */
    def nullableListReads[T](implicit r: Reads[T]): Reads[List[T]] = new Reads[List[T]] {
      def reads(json: JsValue): JsResult[List[T]] = {
        path.asSingleJsResult(json).fold(
          invalid = { err =>
            JsSuccess[List[T]](List.empty[T], path)
          },
          valid = { v =>
            v.validate[List[T]](Reads.list(r))
          }
        )
      }
    }

    /** Write a list if it is non-empty, otherwise nothing. */
    def nullableListWrites[T](implicit w: Writes[T]): OWrites[List[T]] = {
      new OWrites[List[T]] {
        def writes(o: List[T]): JsObject =
          if (o.isEmpty) Json.obj() else path.write[List[T]].writes(o)
      }
    }

    def nullableListFormat[T](implicit fmt: Format[T]): OFormat[List[T]] =
      OFormat[List[T]](nullableListReads(fmt), nullableListWrites(fmt))

    def lazyNullableListReads[T](r: => Reads[T]): Reads[List[T]] =
      Reads(js => nullableListReads(r).reads(js))

    def lazyNullableListWrites[T](w: => Writes[T]): OWrites[List[T]] =
      OWrites((t: List[T]) => nullableListWrites[T](w).writes(t).as[JsObject])

    def lazyNullableListFormat[T](fmt: => Format[T]): OFormat[List[T]] =
      OFormat[List[T]](lazyNullableListReads(fmt), lazyNullableListWrites(fmt))

    /** Read the first item from a list that may be none, if
     * the path is missing.
     */
    def nullableHeadReads[T](implicit r: Reads[T]): Reads[Option[T]] =
      nullableListReads(r).map(_.headOption)

    /** Lazy variant of [[models.json.JsPathExtensions.nullableHeadReads]]. */
    def lazyNullableHeadReads[T](r: => Reads[T]): Reads[Option[T]] =
      Reads(js => nullableHeadReads(r).reads(js))
  }
}
