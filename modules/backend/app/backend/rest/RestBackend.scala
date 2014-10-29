package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc.Headers
import play.api.libs.ws.WSResponse
import backend._

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
case class RestBackend(eventHandler: EventHandler)(implicit val app: play.api.Application)
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
  def query(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[WSResponse]
      = api.get(urlpart, headers, params)

  // Helpers
  def createNewUserProfile[T <: WithId](data: Map[String,String] = Map.empty, groups: Seq[String] = Seq.empty)(implicit apiUser: ApiUser, rd: BackendReadable[T], executionContext: ExecutionContext): Future[T] =
    admin.createNewUserProfile[T](data, groups)
 }
