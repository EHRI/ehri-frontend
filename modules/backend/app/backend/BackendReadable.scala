package backend

import play.api.libs.json.Reads

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait BackendReadable[T] {
  val restReads: Reads[T]
}
