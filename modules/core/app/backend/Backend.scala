package backend

import models.json.RestReadable
import scala.concurrent.Future
import models.UserProfile
import play.api.mvc.Headers
import play.api.libs.ws.Response

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Backend extends Generic with Permissions with Descriptions with Links with Annotations with Events {

  // Visibility
  def setVisibility[MT](id: String, data: List[String])(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[MT]

  // Direct API queries
  def query(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty)(implicit apiUser: ApiUser): Future[Response]

  // Helpers
  def createNewUserProfile(implicit apiUser: ApiUser = ApiUser()): Future[UserProfile]
}
