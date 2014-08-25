package backend

import defines.ContentTypes

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait BackendContentType[T] extends BackendResource[T] {
  /**
   * The content type of the resource.
   */
  def contentType: ContentTypes.Value
}
