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
  with RestVirtualCollections
  with RestLinks
  with RestEvents
  with RestSocial
  with RestVisibility
  with RestPromotion {

  private val api = new ApiDAO
  private val admin = new AdminDAO(eventHandler)

  override def withEventHandler(eventHandler: EventHandler) = this.copy(eventHandler = eventHandler)

  // Direct API query
  def query(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[WSResponse]
      = api.get(urlpart, headers, params)

  // Helpers
  def createNewUserProfile[T <: WithId](data: Map[String,String] = Map.empty, groups: Seq[String] = Seq.empty)(implicit apiUser: ApiUser, rd: BackendReadable[T], executionContext: ExecutionContext): Future[T] =
    admin.createNewUserProfile[T](data, groups)

  // Fetch any type of object. This doesn't really belong here...
  def getAny[MT](id: String)(implicit apiUser: ApiUser,  rd: BackendReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    val url: String = enc(baseUrl, "entities", id)
    BackendRequest(url).withHeaders(authHeaders.toSeq: _*).get().map { response =>
      checkErrorAndParse(response, context = Some(url))(rd.restReads)
    }
  }
}

object RestBackend {
  def withNoopHandler(implicit app: play.api.Application): Backend = new RestBackend(new EventHandler {
    def handleCreate(id: String) = ()
    def handleUpdate(id: String) = ()
    def handleDelete(id: String) = ()
  })
}
