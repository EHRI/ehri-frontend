package backend

import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Promotion {
  def promote[MT](id: String)(implicit rs: BackendResource[MT]): Future[MT]
  def removePromotion[MT](id: String)(implicit rs: BackendResource[MT]): Future[MT]
  def demote[MT](id: String)(implicit rs: BackendResource[MT]): Future[MT]
  def removeDemotion[MT](id: String)(implicit rs: BackendResource[MT]): Future[MT]
}
