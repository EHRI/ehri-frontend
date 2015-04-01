package backend

import play.api.libs.json.Format

/**
 * A type class for items that can be saved (written)
 * to the backend.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Writable[T] {
  val restFormat: Format[T]
}
