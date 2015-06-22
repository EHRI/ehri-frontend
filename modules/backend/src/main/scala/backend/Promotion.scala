package backend

import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Promotion {
  def promote[MT: Resource](id: String): Future[MT]
  def removePromotion[MT: Resource](id: String): Future[MT]
  def demote[MT: Resource](id: String): Future[MT]
  def removeDemotion[MT: Resource](id: String): Future[MT]
}
