package backend

import defines.ContentTypes

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait ContentType[T] extends Resource[T] {
  /**
   * The content type of the resource.
   */
  def contentType: ContentTypes.Value
}
