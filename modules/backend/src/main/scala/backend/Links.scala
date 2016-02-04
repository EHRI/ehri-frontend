package backend

import scala.concurrent.Future
import utils.Page

trait Links {
  def getLinksForItem[A: Readable](id: String): Future[Page[A]]

  def linkItems[MT: Resource, A <: WithId : Readable, AF: Writable](id: String, src: String, link: AF, accessPoint: Option[String] = None): Future[A]

  def deleteLink[MT: Resource](id: String, linkId: String): Future[Boolean]

  def linkMultiple[MT: Resource, A <: WithId : Readable, AF: Writable](id: String, srcToLinks: Seq[(String, AF, Option[String])]): Future[Seq[A]]
}
