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
    def readSeqOrSingle[T](implicit r: Reads[T]): Reads[Seq[T]] =
      path.read[Seq[T]].orElse(path.readNullable[T].map(_.toSeq))

    /** Lazy variant of [[models.json.JsPathExtensions.readSeqOrSingle]]. */
    def lazyReadSeqOrSingle[T](r: => Reads[T]): Reads[Seq[T]] =
      Reads(js => readSeqOrSingle(r).reads(js))

    /** Nullable variant of [[models.json.JsPathExtensions.readSeqOrSingle]]. */
    def readSeqOrSingleNullable[T](implicit r: Reads[T]): Reads[Option[Seq[T]]] =
      path.readNullable[Seq[T]].orElse(path.readNullable[T].map(s => s.map(Seq(_))))

    /** Lazy variant of [[models.json.JsPathExtensions.readSeqOrSingleNullable]]. */
    def lazyReadSeqOrSingleNullable[T](r: => Reads[T]): Reads[Option[Seq[T]]] =
      Reads(js => readSeqOrSingleNullable(r).reads(js))

    /** Attempt to read a path, falling back on a default value. */
    def readWithDefault[T](t: T)(implicit r: Reads[T]): Reads[T] =
      path.read[T].orElse(Reads.pure(t))

    /** Attempt to read a list, falling back on an empty list if the
     * path does not exist.
     */
    def nullableSeqReads[T](implicit r: Reads[T]): Reads[Seq[T]] = new Reads[Seq[T]] {
      def reads(json: JsValue): JsResult[Seq[T]] = {
        path.asSingleJsResult(json).fold(
          invalid = { err =>
            JsSuccess[Seq[T]](Seq.empty[T], path)
          },
          valid = { v =>
            v.validate[Seq[T]](Reads.seq(r))
          }
        )
      }
    }

    /** Write a list if it is non-empty, otherwise nothing. */
    def nullableSeqWrites[T](implicit w: Writes[T]): OWrites[Seq[T]] = {
      new OWrites[Seq[T]] {
        def writes(o: Seq[T]): JsObject =
          if (o.isEmpty) Json.obj() else path.write[Seq[T]].writes(o)
      }
    }

    def nullableSeqFormat[T](implicit fmt: Format[T]): OFormat[Seq[T]] =
      OFormat[Seq[T]](nullableSeqReads(fmt), nullableSeqWrites(fmt))

    def lazyNullableSeqReads[T](r: => Reads[T]): Reads[Seq[T]] =
      Reads(js => nullableSeqReads(r).reads(js))

    def lazyNullableSeqWrites[T](w: => Writes[T]): OWrites[Seq[T]] =
      OWrites((t: Seq[T]) => nullableSeqWrites[T](w).writes(t).as[JsObject])

    def lazyNullableSeqFormat[T](fmt: => Format[T]): OFormat[Seq[T]] =
      OFormat[Seq[T]](lazyNullableSeqReads(fmt), lazyNullableSeqWrites(fmt))

    /** Read the first item from a list that may be none, if
     * the path is missing.
     */
    def nullableHeadReads[T](implicit r: Reads[T]): Reads[Option[T]] =
      nullableSeqReads(r).map(_.headOption)

    /** Lazy variant of [[models.json.JsPathExtensions.nullableHeadReads]]. */
    def lazyNullableHeadReads[T](r: => Reads[T]): Reads[Option[T]] =
      Reads(js => nullableHeadReads(r).reads(js))
  }
}
