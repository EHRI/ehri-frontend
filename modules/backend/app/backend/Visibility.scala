package backend

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Visibility {
  def setVisibility[MT](id: String, data: List[String])(implicit apiUser: ApiUser, rd: BackendReadable[MT], executionContext: ExecutionContext): Future[MT]
}
