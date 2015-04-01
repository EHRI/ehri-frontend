package backend

import defines.ContentTypes

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait BackendContentType[T] extends Resource[T] {
  /**
   * The content type of the resource.
   */
  def contentType: ContentTypes.Value
}
