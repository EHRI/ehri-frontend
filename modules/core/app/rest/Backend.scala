package rest

import models.json.{RestConvertable, RestResource, RestReadable}
import scala.concurrent.Future
import utils.{SystemEventParams, ListParams, PageParams}
import models._
import models.base.{Accessor, AnyModel}
import acl.{ItemPermissionSet, GlobalPermissionSet}
import defines.{EntityType, ContentTypes}
import play.api.libs.json.JsObject
import play.api.mvc.Headers
import play.api.libs.ws.Response

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Backend {

  // Generic CRUD
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

  // Descriptions
  def createDescription[MT,DT](id: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[MT]
  def updateDescription[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[MT]
  def deleteDescription[MT](id: String, did: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Boolean]
  def createAccessPoint[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[(MT,DT)]
  def deleteAccessPoint[MT <: AnyModel](id: String, did: String, apid: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[MT]
  def deleteAccessPoint(id: String)(implicit apiUser: ApiUser): Future[Boolean]

  // Annotations
  def getAnnotationsForItem(id: String)(implicit apiUser: ApiUser): Future[Map[String,List[Annotation]]]
  def createAnnotation(id: String, ann: AnnotationF)(implicit apiUser: ApiUser): Future[Annotation]

  // Links
  def getLinksForItem(id: String)(implicit apiUser: ApiUser): Future[List[Link]]
  def linkItems(id: String, src: String, link: LinkF, accessPoint: Option[String] = None)(implicit apiUser: ApiUser): Future[Link]
  def deleteLink(id: String, linkId: String)(implicit apiUser: ApiUser): Future[Boolean]
  def linkMultiple(id: String, srcToLinks: List[(String,LinkF,Option[String])])(implicit apiUser: ApiUser): Future[List[Link]]

  // Permissions
  def getItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String)(implicit apiUser: ApiUser): Future[ItemPermissionSet[T]]
  def setItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String, data: List[String])(implicit apiUser: ApiUser): Future[ItemPermissionSet[T]]
  def setGlobalPermissions[T <: Accessor](user: T, data: Map[String, List[String]])(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]]
  def getGlobalPermissions[T <: Accessor](user: T)(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]]
  def getScopePermissions[T <: Accessor](user: T, id: String)(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]]
  def setScopePermissions[T <: Accessor](user: T, id: String, data: Map[String,List[String]])(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]]
  def listPermissionGrants[T <: Accessor](user: T, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]]
  def listItemPermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]]
  def listScopePermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]]
  def addGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Boolean]
  def removeGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Boolean]

  // Events
  def subjectsForEvent(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[AnyModel]]
  def listEvents(params: ListParams, filters: SystemEventParams)(implicit apiUser: ApiUser): Future[List[SystemEvent]]
  def history(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[SystemEvent]]

  // Visibility
  def setVisibility[MT](id: String, data: List[String])(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[MT]

  // Direct API queries
  def query(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty)(implicit apiUser: ApiUser): Future[Response]

  // Helpers
  def createNewUserProfile(implicit apiUser: ApiUser = ApiUser()): Future[UserProfile]
}
