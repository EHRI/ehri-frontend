package backend

import scala.concurrent.Future
import models.UserProfile
import play.api.mvc.Headers
import play.api.libs.ws.Response

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Backend extends Generic with Permissions with Descriptions with Links with Annotations with Visibility with Events with Social {

  // Direct API queries
  def query(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty)(implicit apiUser: ApiUser): Future[Response]

  // Helpers
  def createNewUserProfile(data: Map[String,String] = Map.empty)(implicit apiUser: ApiUser = ApiUser()): Future[UserProfile]
}
