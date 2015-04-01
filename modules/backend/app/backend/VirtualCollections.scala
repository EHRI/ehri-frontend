package backend

import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait VirtualCollections {
  /**
   * Include a set of items in a virtual collection.
   * 
   * @param vcId the virtual collection id
   * @param ids a set of item ids
   */
  def addReferences[MT](vcId: String, ids: Seq[String])(implicit rs: Resource[MT]): Future[Unit]

  /**
   * Remove a set of items from a virtual collection.
   *
   * @param vcId the virtual collection id
   * @param ids a set of item ids
   */
  def deleteReferences[MT](vcId: String, ids: Seq[String])(implicit rs: Resource[MT]): Future[Unit]

  /**
   * Move a set of items from one virtual collection to another
   *
   * @param fromVc the source virtual collection id
   * @param toVc the destination virtual collection id
   * @param ids a set of item ids
   */
  def moveReferences[MT](fromVc: String, toVc: String, ids: Seq[String])(implicit rs: Resource[MT]): Future[Unit]
}
