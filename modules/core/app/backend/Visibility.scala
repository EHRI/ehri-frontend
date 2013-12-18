package backend

import models.json.RestReadable
import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Visibility {
  def setVisibility[MT](id: String, data: List[String])(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[MT]
  def promote(id: String)(implicit apiUser: ApiUser): Future[Boolean]
  def demote(id: String)(implicit apiUser: ApiUser): Future[Boolean]
}
