package backend

import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Visibility {
  def setVisibility[MT: Resource](id: String, data: Seq[String]): Future[MT]
}
