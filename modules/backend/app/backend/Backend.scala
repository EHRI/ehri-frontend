package backend

import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc.Headers
import play.api.libs.ws.WSResponse

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Backend
  extends Generic
  with Permissions
  with Descriptions
  with Links
  with Annotations
  with Visibility
  with Promotion
  with Events
  with Social {

  // Direct API queries
  def query(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[WSResponse]

  // Helpers
  def createNewUserProfile[T <: WithId](data: Map[String,String] = Map.empty, groups: Seq[String] = Seq.empty)(implicit apiUser: ApiUser, rd: BackendReadable[T], executionContext: ExecutionContext): Future[T]
}
