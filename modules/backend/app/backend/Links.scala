package backend

import scala.concurrent.Future
import utils.Page

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Links {
  def getLinksForItem[A](id: String)(implicit rd: BackendReadable[A]): Future[Page[A]]

  def linkItems[MT, A <: WithId, AF](id: String, src: String, link: AF, accessPoint: Option[String] = None)(implicit rs: Resource[MT], rd: BackendReadable[A], wd: BackendWriteable[AF]): Future[A]

  def deleteLink[MT](id: String, linkId: String)(implicit rs: Resource[MT]): Future[Boolean]

  def linkMultiple[MT, A <: WithId, AF](id: String, srcToLinks: Seq[(String, AF, Option[String])])(implicit rs: Resource[MT], rd: BackendReadable[A], wd: BackendWriteable[AF]): Future[Seq[A]]
}
