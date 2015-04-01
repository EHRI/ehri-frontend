package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.{Page, PageParams}
import defines.ContentTypes
import play.api.libs.json.JsObject

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Generic {
  def get[MT](resource: BackendResource[MT], id: String)(implicit executionContext: ExecutionContext): Future[MT]

  def get[MT](id: String)(implicit rs: BackendResource[MT], executionContext: ExecutionContext): Future[MT]

  def getJson[MT](id: String)(implicit rs: BackendResource[MT], executionContext: ExecutionContext): Future[JsObject]

  def create[MT <: WithId, T](item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None)(implicit rs: BackendResource[MT], wrt: BackendWriteable[T], executionContext: ExecutionContext): Future[MT]

  def createInContext[MT, T, TT <: WithId](id: String, contentType: ContentTypes.Value, item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map(), logMsg: Option[String] = None)(implicit wrt: BackendWriteable[T], rs: BackendResource[MT], rd: BackendReadable[TT], executionContext: ExecutionContext): Future[TT]

  def update[MT, T](id: String, item: T, logMsg: Option[String] = None)(implicit wrt: BackendWriteable[T], rs: BackendResource[MT], executionContext: ExecutionContext): Future[MT]

  def patch[MT](id: String, data: JsObject, logMsg: Option[String] = None)(implicit rs: BackendResource[MT], executionContext: ExecutionContext): Future[MT]

  def delete[MT](id: String, logMsg: Option[String] = None)(implicit rs: BackendResource[MT], executionContext: ExecutionContext): Future[Unit]

  def listJson[MT](params: PageParams = PageParams.empty)(implicit rs: BackendResource[MT], executionContext: ExecutionContext): Future[Page[JsObject]]

  def list[MT](params: PageParams = PageParams.empty)(implicit rs: BackendResource[MT], executionContext: ExecutionContext): Future[Page[MT]]

  def listChildren[MT, CMT](id: String, params: PageParams = PageParams.empty)(implicit rs: BackendResource[MT], rd: BackendReadable[CMT], executionContext: ExecutionContext): Future[Page[CMT]]

  def count[MT](params: PageParams = PageParams.empty)(implicit rs: BackendResource[MT], executionContext: ExecutionContext): Future[Long]

  def countChildren[MT](id: String, params: PageParams = PageParams.empty)(implicit rs: BackendResource[MT], executionContext: ExecutionContext): Future[Long]
}
