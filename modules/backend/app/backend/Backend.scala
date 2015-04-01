package backend

import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc.Headers
import play.api.libs.ws.WSResponse

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */

trait Backend {
  def withContext(apiUser: ApiUser)(implicit executionContext: ExecutionContext): BackendHandle
}

trait BackendHandle
  extends Generic
  with Permissions
  with Descriptions
  with Links
  with Annotations
  with VirtualCollections
  with Visibility
  with Promotion
  with Events
  with Social {

  def eventHandler: EventHandler
  def withEventHandler(eventHandler: EventHandler): BackendHandle

  // Direct API queries
  def query(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty): Future[WSResponse]

  // Helpers
  def createNewUserProfile[T <: WithId](data: Map[String,String] = Map.empty, groups: Seq[String] = Seq.empty)(implicit rd: Readable[T]): Future[T]

  // Fetch any type of object
  def getAny[MT](id: String)(implicit rd: Readable[MT]): Future[MT]
}
