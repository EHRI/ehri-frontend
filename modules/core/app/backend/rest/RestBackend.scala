package backend.rest

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
import backend.{EventHandler, ApiUser, Backend, Page}

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
case class RestBackend(eventHandler: EventHandler) extends Backend {

  private val generic = new EntityDAO(eventHandler)
  private val descriptions = new DescriptionDAO(eventHandler)
  private val perms = new PermissionDAO(eventHandler)
  private val links = new LinkDAO(eventHandler)
  private val annotations = new AnnotationDAO(eventHandler)
  private val events = new SystemEventDAO
  private val visibility = new VisibilityDAO(eventHandler)
  private val api = new ApiDAO
  private val admin = new AdminDAO(eventHandler)

   // Generic CRUD
   def get[MT](entityType: EntityType.Value, id: String)(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[MT]
      = generic.get(entityType, id)

   def get[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[MT]
      = generic.get(id)

   def getJson[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[JsObject]
      = generic.getJson(id)

   def get[MT](key: String, value: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[MT]
      = generic.get(key, value)

   def create[MT,T](item: T, accessors: List[String] = Nil,params: Map[String,Seq[String]] = Map(), logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], wrt: RestConvertable[T], rd: RestReadable[MT]): Future[MT]
      = generic.create(item, accessors, params, logMsg)

   def createInContext[MT,T,TT](id: String, contentType: ContentTypes.Value, item: T, accessors: List[String] = Nil, logMsg: Option[String] = None)(implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[TT]): Future[TT]
      = generic.createInContext(id, contentType, item, accessors, logMsg)

   def update[MT,T](id: String, item: T, logMsg: Option[String] = None)(implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[MT]): Future[MT]
      = generic.update(id, item, logMsg)

   def delete[MT](entityType: EntityType.Value, id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser): Future[Boolean]
      = generic.delete(entityType, id, logMsg)

  def delete[MT](id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Boolean]
      = generic.delete(id, logMsg)

  def listJson[MT](params: ListParams = ListParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[List[JsObject]]
      = generic.listJson(params)

  def list[MT](params: ListParams = ListParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[List[MT]]
      = generic.list(params)

  def listChildren[MT,CMT](id: String, params: ListParams = ListParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[CMT]): Future[List[CMT]]
      = generic.listChildren(id, params)

  def pageJson[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Page[JsObject]]
      = generic.pageJson(params)

  def page[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Page[MT]]
      = generic.page(params)

  def pageChildren[MT,CMT](id: String, params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[CMT]): Future[Page[CMT]]
      = generic.pageChildren(id, params)

  def count[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Long]
      = generic.count(params)

  def countChildren[MT](id: String, params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Long]
      = generic.countChildren(id, params)

  // Descriptions
   def createDescription[MT,DT](id: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[MT]
      = descriptions.createDescription(id, item, logMsg)

   def updateDescription[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[MT]
      = descriptions.updateDescription(id, did, item, logMsg)

   def deleteDescription[MT](id: String, did: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Boolean]
      = descriptions.deleteDescription(id, did, logMsg)

   def createAccessPoint[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[(MT,DT)]
      = descriptions.createAccessPoint(id, did, item, logMsg)

   def deleteAccessPoint[MT <: AnyModel](id: String, did: String, apid: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[MT]
      = descriptions.deleteAccessPoint(id, did, apid, logMsg)

  def deleteAccessPoint(id: String)(implicit apiUser: ApiUser): Future[Boolean]
      = descriptions.deleteAccessPoint(id)

  // Annotations
   def getAnnotationsForItem(id: String)(implicit apiUser: ApiUser): Future[Map[String,List[Annotation]]]
      = annotations.getAnnotationsForItem(id)

   def createAnnotation(id: String, ann: AnnotationF)(implicit apiUser: ApiUser): Future[Annotation]
      = annotations.createAnnotation(id, ann)

   // Links
   def getLinksForItem(id: String)(implicit apiUser: ApiUser): Future[List[Link]]
      = links.getLinksForItem(id)

   def linkItems(id: String, src: String, link: LinkF, accessPoint: Option[String] = None)(implicit apiUser: ApiUser): Future[Link]
      = links.linkItems(id, src, link, accessPoint)

   def deleteLink(id: String, linkId: String)(implicit apiUser: ApiUser): Future[Boolean]
      = links.deleteLink(id, linkId)

   def linkMultiple(id: String, srcToLinks: List[(String,LinkF,Option[String])])(implicit apiUser: ApiUser): Future[List[Link]]
      = links.linkMultiple(id, srcToLinks)

   // Permissions
   def getItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String)(implicit apiUser: ApiUser): Future[ItemPermissionSet[T]]
      = perms.getItemPermissions(user, contentType, id)

   def setItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String, data: List[String])(implicit apiUser: ApiUser): Future[ItemPermissionSet[T]]
      = perms.setItemPermissions(user, contentType, id, data)

   def setGlobalPermissions[T <: Accessor](user: T, data: Map[String, List[String]])(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]]
      = perms.setGlobalPermissions(user, data)

   def getGlobalPermissions[T <: Accessor](user: T)(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]]
      = perms.getGlobalPermissions(user)

   def getScopePermissions[T <: Accessor](user: T, id: String)(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]]
      = perms.getScopePermissions(user, id)

   def setScopePermissions[T <: Accessor](user: T, id: String, data: Map[String,List[String]])(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]]
      = perms.setScopePermissions(user, id, data)

   def listPermissionGrants[T <: Accessor](user: T, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]]
      = perms.listPermissionGrants(user, params)

   def listItemPermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]]
      = perms.listItemPermissionGrants(id, params)

   def listScopePermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]]
      = perms.listScopePermissionGrants(id, params)

   def addGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Boolean]
      = perms.addGroup(groupId, userId)

   def removeGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Boolean]
      = perms.removeGroup(groupId, userId)

   // Events
   def subjectsForEvent(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[AnyModel]]
      = events.subjectsForEvent(id, params)

   def listEvents(params: ListParams, filters: SystemEventParams)(implicit apiUser: ApiUser): Future[List[SystemEvent]]
      = events.listEvents(params, filters)

   def history(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[SystemEvent]]
      = events.history(id, params)

   // Visibility
   def setVisibility[MT](id: String, data: List[String])(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[MT]
      = visibility.setVisibility(id, data)

  // Direct API query
  def query(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty)(implicit apiUser: ApiUser): Future[Response]
      = api.get(urlpart, headers, params)

  // Helpers
  def createNewUserProfile(implicit apiUser: ApiUser = ApiUser()): Future[UserProfile]
    = admin.createNewUserProfile
 }
