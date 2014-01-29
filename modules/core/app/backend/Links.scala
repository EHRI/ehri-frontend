package backend

import scala.concurrent.{ExecutionContext, Future}
import models.{Link,LinkF}

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
trait Links {
  def getLinksForItem(id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[List[Link]]
  def linkItems(id: String, src: String, link: LinkF, accessPoint: Option[String] = None)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Link]
  def deleteLink(id: String, linkId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]
  def linkMultiple(id: String, srcToLinks: List[(String,LinkF,Option[String])])(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[List[Link]]
}
