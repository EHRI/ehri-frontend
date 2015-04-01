package backend

import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Visibility {
  def setVisibility[MT](id: String, data: Seq[String])(implicit rs: BackendResource[MT]): Future[MT]
}
