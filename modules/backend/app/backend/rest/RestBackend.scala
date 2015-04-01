package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc.Headers
import play.api.libs.ws.WSResponse
import backend._

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
case class RestBackend(eventHandler: EventHandler)(implicit app: play.api.Application)
  extends Backend {
  def withContext(apiUser: ApiUser)(implicit executionContext: ExecutionContext) = new RestBackendHandle(eventHandler)(app, apiUser, executionContext)
}

case class RestBackendHandle(eventHandler: EventHandler)(implicit val app: play.api.Application, val apiUser: ApiUser, val executionContext: ExecutionContext)
  extends BackendHandle
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
  override def query(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty): Future[WSResponse]
      = api.get(urlpart, headers, params)

  // Helpers
  override def createNewUserProfile[T <: WithId](data: Map[String,String] = Map.empty, groups: Seq[String] = Seq.empty)(implicit rd: BackendReadable[T]): Future[T] =
    admin.createNewUserProfile[T](data, groups)

  // Fetch any type of object. This doesn't really belong here...
  override def getAny[MT](id: String)(implicit rd: BackendReadable[MT]): Future[MT] = {
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
