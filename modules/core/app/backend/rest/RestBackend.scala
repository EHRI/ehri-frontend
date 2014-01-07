package backend.rest

import scala.concurrent.Future
import models._
import play.api.mvc.Headers
import play.api.libs.ws.Response
import backend.{EventHandler, ApiUser, Backend}

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
case class RestBackend(eventHandler: EventHandler)
  extends Backend
  with RestGeneric
  with RestPermissions
  with RestDescriptions
  with RestAnnotations
  with RestLinks
  with RestEvents
  with RestSocial
  with RestVisibility {

  private val api = new ApiDAO
  private val admin = new AdminDAO(eventHandler)

  // Direct API query
  def query(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty)(implicit apiUser: ApiUser): Future[Response]
      = api.get(urlpart, headers, params)

  // Helpers
  def createNewUserProfile(data: Map[String,String] = Map.empty)(implicit apiUser: ApiUser = ApiUser()): Future[UserProfile]
    = admin.createNewUserProfile(data)
 }
