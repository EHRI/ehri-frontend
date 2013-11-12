package backend

import models.json.{ClientConvertable, RestReadable}
import play.api.libs.json.{Writes, Reads}

/**
 * Class representing a page of data.
 */
case class Page[+T](
  total: Long,
  offset: Int,
  limit: Int,
  items: Seq[T]
) extends utils.AbstractPage[T]

object Page {

  implicit def restReads[T](implicit apiUser: ApiUser, rd: RestReadable[T]): Reads[Page[T]] = {
    Page.pageReads(rd.restReads)
  }
  implicit def clientFormat[T](implicit cfmt: ClientConvertable[T]): Writes[Page[T]] = {
    Page.pageWrites(cfmt.clientFormat)
  }

  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit def pageReads[T](implicit r: Reads[T]): Reads[Page[T]] = (
    (__ \ "total").read[Long] and
    (__ \ "offset").read[Int] and
    (__ \ "limit").read[Int] and
    (__ \ "values").lazyRead(Reads.seq[T](r))
  )(Page.apply[T] _)

  implicit def pageWrites[T](implicit r: Writes[T]): Writes[Page[T]] = (
    (__ \ "total").write[Long] and
    (__ \ "offset").write[Int] and
    (__ \ "limit").write[Int] and
    (__ \ "values").lazyWrite(Writes.seq[T](r))
  )(unlift(Page.unapply[T] _))

  implicit def pageFormat[T](implicit r: Reads[T], w: Writes[T]): Format[Page[T]]
    = Format(pageReads(r), pageWrites(w))
}

