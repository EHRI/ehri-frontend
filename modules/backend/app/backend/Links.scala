package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.Page

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Links {
  def getLinksForItem[A](id: String)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def linkItems[MT, A <: WithId, AF](id: String, src: String, link: AF, accessPoint: Option[String] = None)(implicit apiUser: ApiUser, rs: BackendResource[MT], rd: BackendReadable[A], wd: BackendWriteable[AF], executionContext: ExecutionContext): Future[A]

  def deleteLink[MT](id: String, linkId: String)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Boolean]

  def linkMultiple[MT, A <: WithId, AF](id: String, srcToLinks: Seq[(String, AF, Option[String])])(implicit apiUser: ApiUser, rs: BackendResource[MT], rd: BackendReadable[A], wd: BackendWriteable[AF], executionContext: ExecutionContext): Future[Seq[A]]
}
