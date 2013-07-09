package models.json

import play.api.libs.json.{Reads, Writes, Format}

/**
 * Created with IntelliJ IDEA.
 * User: michaelb
 * Date: 21/06/13
 * Time: 13:07
 * To change this template use File | Settings | File Templates.
 */

trait RestReadable[T] {
  implicit val restReads: Reads[T]
}

trait RestConvertable[T] {
  implicit val restFormat: Format[T]
}

trait ClientConvertable[T] {
  implicit val clientFormat: Writes[T]
}
