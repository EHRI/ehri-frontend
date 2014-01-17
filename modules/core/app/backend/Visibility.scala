package backend

import models.json.RestReadable
import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Visibility {
  def setVisibility[MT](id: String, data: List[String])(implicit apiUser: ApiUser, rd: RestReadable[MT], executionContext: ExecutionContext): Future[MT]
  def promote(id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]
  def demote(id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]
}
