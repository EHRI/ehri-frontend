package utils

import scala.concurrent.Future

/**
 * Component that manages references to pages that have moved
 * and now point somewhere else.
 */
trait MovedPageLookup {

  /**
   * Check if a given app page has been relocated.
   *
   * @param path the path, e.g. /units/bar
   * @return an optional destination, if one exists
   */
  def hasMovedTo(path: String): Future[Option[String]]

  /**
   * Add a set of moved pages.
   * @param moved a sequence of the original and new pages
   * @return the number that have been inserted.
   */
  def addMoved(moved: Seq[(String, String)]): Future[Int]

}
