package backend

import models.json.{RestConvertable, RestResource, RestReadable}
import scala.concurrent.Future
import utils.{ListParams, PageParams}
import defines.{EntityType, ContentTypes}
import play.api.libs.json.JsObject

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
trait Generic {
  def get[MT](entityType: EntityType.Value, id: String)(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[MT]
  def get[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[MT]
  def getJson[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[JsObject]
  def get[MT](key: String, value: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[MT]
  def create[MT,T](item: T, accessors: List[String] = Nil,params: Map[String,Seq[String]] = Map(), logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], wrt: RestConvertable[T], rd: RestReadable[MT]): Future[MT]
  def createInContext[MT,T,TT](id: String, contentType: ContentTypes.Value, item: T, accessors: List[String] = Nil, logMsg: Option[String] = None)(implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[TT]): Future[TT]
  def update[MT,T](id: String, item: T, logMsg: Option[String] = None)(implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[MT]): Future[MT]
  def delete[MT](entityType: EntityType.Value, id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser): Future[Boolean]
  def delete[MT](id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Boolean]

  def listJson[MT](params: ListParams = ListParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[List[JsObject]]
  def list[MT](params: ListParams = ListParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[List[MT]]
  def listChildren[MT,CMT](id: String, params: ListParams = ListParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[CMT]): Future[List[CMT]]
  def pageJson[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Page[JsObject]]
  def page[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Page[MT]]
  def pageChildren[MT,CMT](id: String, params: PageParams = utils.PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[CMT]): Future[Page[CMT]]
  def count[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Long]
  def countChildren[MT](id: String, params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Long]
}
