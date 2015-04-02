package backend

import scala.concurrent.Future
import utils.{Page, PageParams}
import defines.ContentTypes
import play.api.libs.json.JsObject

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Generic {
  def get[MT](resource: Resource[MT], id: String): Future[MT]

  def get[MT: Resource](id: String): Future[MT]

  def getJson[MT: Resource](id: String): Future[JsObject]

  def create[MT <: WithId :  Resource, T: Writable](item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[MT]

  def createInContext[MT: Resource, T: Writable, TT <: WithId : Readable](id: String, contentType: ContentTypes.Value, item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map(), logMsg: Option[String] = None): Future[TT]

  def update[MT: Resource, T: Writable](id: String, item: T, logMsg: Option[String] = None): Future[MT]

  def patch[MT](id: String, data: JsObject, logMsg: Option[String] = None)(implicit rs: Resource[MT]): Future[MT]

  def delete[MT: Resource](id: String, logMsg: Option[String] = None): Future[Unit]

  def listJson[MT: Resource](params: PageParams = PageParams.empty): Future[Page[JsObject]]

  def list[MT: Resource](params: PageParams = PageParams.empty): Future[Page[MT]]

  def listChildren[MT: Resource, CMT: Readable](id: String, params: PageParams = PageParams.empty): Future[Page[CMT]]

  def count[MT: Resource](params: PageParams = PageParams.empty): Future[Long]

  def countChildren[MT: Resource](id: String, params: PageParams = PageParams.empty): Future[Long]
}
