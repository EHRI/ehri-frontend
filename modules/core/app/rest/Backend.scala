package rest

import models.json.{RestConvertable, RestResource, RestReadable}
import scala.concurrent.Future
import utils.{SystemEventParams, ListParams, PageParams}
import models._
import models.base.{Accessor, AnyModel}
import acl.{ItemPermissionSet, GlobalPermissionSet}
import defines.{EntityType, ContentTypes}
import play.api.libs.json.JsObject

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Backend {

  // Generic CRUD
  def get[MT](entityType: EntityType.Value, id: String)(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[Either[RestError, MT]]
  def get[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, MT]]
  def getJson[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Either[RestError, JsObject]]
  def get[MT](key: String, value: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, MT]]
  def create[MT,T](item: T, accessors: List[String] = Nil,params: Map[String,Seq[String]] = Map(), logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], wrt: RestConvertable[T], rd: RestReadable[MT]): Future[Either[RestError, MT]]
  def createInContext[MT,T,TT](id: String, contentType: ContentTypes.Value, item: T, accessors: List[String] = Nil, logMsg: Option[String] = None)(implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[TT]): Future[Either[RestError, TT]]
  def update[MT,T](id: String, item: T, logMsg: Option[String] = None)(implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, MT]]
  def delete[MT](entityType: EntityType.Value, id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser): Future[Either[RestError, Boolean]]

  // Descriptions
  def createDescription[MT,DT](id: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[Either[RestError, MT]]
  def updateDescription[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[Either[RestError, MT]]
  def deleteDescription[MT](id: String, did: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, Boolean]]
  def createAccessPoint[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[Either[RestError, (MT,DT)]]
  def deleteAccessPoint[MT <: AnyModel](id: String, did: String, apid: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, MT]]

  // Annotations
  def getAnnotationsForItem(id: String)(implicit apiUser: ApiUser): Future[Either[RestError, Map[String,List[Annotation]]]]
  def createAnnotation(id: String, ann: AnnotationF)(implicit apiUser: ApiUser): Future[Either[RestError, Annotation]]

  // Links
  def getLinksForItem(id: String)(implicit apiUser: ApiUser): Future[Either[RestError, List[Link]]]
  def linkItems(id: String, src: String, link: LinkF, accessPoint: Option[String] = None)(implicit apiUser: ApiUser): Future[Either[RestError, Link]]
  def deleteLink(id: String, linkId: String)(implicit apiUser: ApiUser): Future[Either[RestError,Boolean]]
  def deleteAccessPoint(id: String)(implicit apiUser: ApiUser): Future[Either[RestError,Boolean]]
  def linkMultiple(id: String, srcToLinks: List[(String,LinkF,Option[String])])(implicit apiUser: ApiUser): Future[Either[RestError, List[Link]]]

  // Permissions
  def getItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String)(implicit apiUser: ApiUser): Future[Either[RestError, ItemPermissionSet[T]]]
  def setItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String, data: List[String])(implicit apiUser: ApiUser): Future[Either[RestError, ItemPermissionSet[T]]]
  def setGlobalPermissions[T <: Accessor](user: T, data: Map[String, List[String]])(implicit apiUser: ApiUser): Future[Either[RestError, GlobalPermissionSet[T]]]
  def getGlobalPermissions[T <: Accessor](user: T)(implicit apiUser: ApiUser): Future[Either[RestError, GlobalPermissionSet[T]]]
  def getScopePermissions[T <: Accessor](user: T, id: String)(implicit apiUser: ApiUser): Future[Either[RestError, GlobalPermissionSet[T]]]
  def setScopePermissions[T <: Accessor](user: T, id: String, data: Map[String,List[String]])(implicit apiUser: ApiUser): Future[Either[RestError, GlobalPermissionSet[T]]]
  def listPermissionGrants[T <: Accessor](user: T, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[PermissionGrant]]]
  def listItemPermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[PermissionGrant]]]
  def listScopePermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[PermissionGrant]]]
  def addGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Either[RestError, Boolean]]
  def removeGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Either[RestError, Boolean]]

  // Events
  def subjectsForEvent(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[AnyModel]]]
  def listEvents(params: ListParams, filters: SystemEventParams)(implicit apiUser: ApiUser): Future[Either[RestError, List[SystemEvent]]]
  def history(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[SystemEvent]]]

  // Visibility
  def setVisibility[MT](id: String, data: List[String])(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[Either[RestError, MT]]
}
