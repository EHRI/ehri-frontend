package backend

import play.api.libs.json.Format

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait BackendWriteable[T] {
  val restFormat: Format[T]
}
