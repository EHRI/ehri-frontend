package models.json

import play.api.libs.json.Format

/**
 * Created with IntelliJ IDEA.
 * User: michaelb
 * Date: 21/06/13
 * Time: 13:07
 * To change this template use File | Settings | File Templates.
 */
trait RestConvertable[T] {
  implicit val restFormat: Format[T]
}

trait ClientConvertable[T] {
  implicit val clientFormat: Format[T]
}
