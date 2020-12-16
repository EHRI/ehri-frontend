package services.data

import acl.{GlobalPermissionSet, ItemPermissionSet}
import akka.stream.scaladsl.Source
import defines.{ContentTypes, EntityType}
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.ws.WSResponse
import play.api.mvc.Headers
import utils._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Factory class for creating data API handles.
  */
trait DataApi {
  /**
    * Get an API handle for the given user.
    *
    * @param apiUser          the current user
    * @param executionContext the execution context
    * @return an API handle
    */
  def withContext(apiUser: ApiUser)(implicit executionContext: ExecutionContext): DataApiHandle
}

/**
  * An API handle, which includes the user and execution
  * context.
  */
trait DataApiHandle {
  /**
    * Get the event handler object for this API handle.
    *
    * @return an event handler
    */
  def eventHandler: EventHandler

  /**
    * Override the event handler for the API handle.
    *
    * @param eventHandler an event handler
    * @return a new API handle
    */
  def withEventHandler(eventHandler: EventHandler): DataApiHandle

  /**
    * Get a service status
    *
    * @return a status message
    */
  def status(): Future[String]

  /**
    * Pass a query directly through to the backend API.
    *
    * @param urlPart the URL backend path
    * @param headers the required headers
    * @param params  additional parameters
    * @return a web response
    */
  def query(urlPart: String, headers: Headers = Headers(), params: Map[String, Seq[String]] = Map.empty): Future[WSResponse]

  /**
    * Pass a query directly through to the backend API and retrieve the result as a stream.
    *
    * @param urlPart the URL backend path
    * @param headers the required headers
    * @param params  additional parameters
    * @return a web response
    */
  def stream(urlPart: String, headers: Headers = Headers(), params: Map[String, Seq[String]] = Map.empty): Future[WSResponse]

  /**
    * Create a new user profile.
    *
    * @param data   the user data
    * @param groups groups to which this user belongs
    * @return the new user item
    */
  def createNewUserProfile[T <: WithId : Readable](data: Map[String, String] = Map.empty, groups: Seq[String] = Seq.empty): Future[T]

  /**
    * Rename a batch of items.
    *
    * @param mapping a mapping of current global ID to new local identifier
    * @return a mapping of old global ID to new (regenerated) global ID
    */
  def rename(mapping: Seq[(String, String)]): Future[Seq[(String, String)]]

  /**
    * Reparent a batch of items.
    *
    * @param mapping a mapping of current global ID to new parent global ID
    * @return a mapping of old global ID to new (regenerated) global ID
    */
  def reparent(mapping: Seq[(String, String)], commit: Boolean = false): Future[Seq[(String, String)]]

  /**
    * Scan for IDs that require regeneration across an entire item type,
    * and optionally regenerate them.
    *
    * @param ct     the content type
    * @param commit whether or not to commit changes
    * @return a mapping of old global ID to new (regenerated) global ID
    */
  def regenerateIdsForType(ct: ContentTypes.Value, tolerant: Boolean = false, commit: Boolean = false): Future[Seq[(String, String)]]

  /**
    * Scan for IDs that require regeneration within a given scope,
    * and optionally regenerate them.
    *
    * @param scope  the scope ID
    * @param commit whether or not to commit changes
    * @return a mapping of old global ID to new (regenerated) global ID
    */
  def regenerateIdsForScope(scope: String, tolerant: Boolean = false, commit: Boolean = false): Future[Seq[(String, String)]]

  /**
    * Check of any of the given items require ID regeneration,
    * and optionally regenerate them.
    *
    * @param ids    the given items
    * @param commit whether or not to commit changes
    * @return a mapping of old global ID to new (regenerated) global ID
    */
  def regenerateIds(ids: Seq[String], tolerant: Boolean = false, commit: Boolean = false): Future[Seq[(String, String)]]

  /**
    * Find and replace a text value for a given property across an entire entity type.
    *
    * @param ct       the parent content type
    * @param et       a specific entity type
    * @param property the property to search within
    * @param from     the current text
    * @param to       the replacement text
    * @param commit   whether to commit the changes
    * @param logMsg   a log message, required when committing changes
    * @return a list of content type ID, entity ID, and the new property value
    */
  def findReplace(ct: ContentTypes.Value, et: EntityType.Value, property: String, from: String, to: String, commit: Boolean, logMsg: Option[String]): Future[Seq[(String, String, String)]]

  /**
    * Delete a batch of items.
    *
    * @param ids     a sequence of item IDs
    * @param scope   an optional item scope
    * @param logMsg  a log message
    * @param version whether or not to create pre-delete versions of the deleted items
    * @param commit  whether to commit the changes
    * @return the number of items deleted
    */
  def batchDelete(ids: Seq[String], scope: Option[String], logMsg: String, version: Boolean, commit: Boolean = false): Future[Int]

  /**
    * Update a batch of items via a stream of partial JSON bundles.
    *
    * @param data    partial model data a JSON stream
    * @param scope   the optional shared item scope
    * @param version whether or not to create pre-update versions of updated items
    * @param commit  whether to commit the changes
    * @return the number of items updated
    */
  def batchUpdate(data: Source[JsValue, _], scope: Option[String], logMsg: String, version: Boolean, commit: Boolean): Future[BatchResult]

  /**
    * Update a batch of items.
    *
    * @param data    the model data
    * @param scope   the optional shared item scope
    * @param version whether or not to create pre-update versions of updated items
    * @param commit  whether to commit the changes
    * @tparam T the generic model data type
    * @return the number of items updated
    */
  def batchUpdate[T: Writable](data: Seq[T], scope: Option[String], logMsg: String, version: Boolean, commit: Boolean): Future[BatchResult]

  /**
    * Fetch any type of item by ID.
    *
    * @param id the string ID
    * @return the item
    */
  def getAny[MT: Readable](id: String): Future[MT]

  /**
    * Fetch items by string ID or internal graph ID.
    *
    * @param ids  a sequence of string IDs
    * @param gids a sequence of graph IDs
    */
  def fetch[MT: Readable](ids: Seq[String] = Seq.empty, gids: Seq[Long] = Seq.empty): Future[Seq[Option[MT]]]

  /**
    * Set visibility for a given item.
    *
    * @param id   the item's id
    * @param data a list of accessor ids
    * @return the updated item
    */
  def setVisibility[MT: Resource](id: String, data: Seq[String]): Future[MT]

  /**
    * Get an item with an explicit resource type and id.
    *
    * @param resource the resource type
    * @param id       the item's id
    * @tparam MT the generic type of the resource
    */
  def get[MT](resource: Resource[MT], id: String): Future[MT]

  /**
    * Get an item woth the given id.
    *
    * @param id the item's id
    * @tparam MT the generic type of the resource
    */
  def get[MT: Resource](id: String): Future[MT]

  /**
    * Create a new item.
    *
    * @param item      the item data
    * @param accessors the users/groups that can initially
    *                  access this item (none implies all)
    * @param params    additional web service parameters
    * @param logMsg    the log message
    * @tparam MT the generic type of the resource
    * @tparam T  the generic type of the resource's data
    */
  def create[MT <: WithId : Resource, T: Writable](item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[MT]

  /**
    * Create a new item in the context of a parent item.
    *
    * @param id        the parent's id
    * @param item      the new item's data
    * @param accessors the users/groups that can initially
    *                  access this item (none implies all)
    * @param params    additional web service parameters
    * @param logMsg    the log message
    * @tparam MT  the generic type of the parent resource
    * @tparam T   the generic type of the child item's data
    * @tparam CMT the generic type of the child item
    */
  def createInContext[MT: Resource, T: Writable, CMT <: WithId : Readable](id: String, item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map(), logMsg: Option[String] = None): Future[CMT]

  /**
    * Update an item.
    *
    * @param id     the item's id
    * @param item   the item's data
    * @param params additional web service parameters
    * @param logMsg the log message
    * @tparam MT the generic type of the item
    * @tparam T  the generic type of the item's data
    */
  def update[MT: Resource, T: Writable](id: String, item: T, params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[MT]

  /**
    * Partially update (patch) an item's properties.
    *
    * @param id     the item's id
    * @param data   a JSON object contain the properties to be
    *               updated
    * @param params additional web service parameters
    * @param logMsg the log message
    * @tparam MT the generic type of the item
    */
  def patch[MT: Resource](id: String, data: JsObject, params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[MT]

  /**
    * Delete an item.
    *
    * @param id     the item's id
    * @param logMsg the log message
    * @tparam MT the generic type of the item
    */
  def delete[MT: Resource](id: String, logMsg: Option[String] = None): Future[Unit]

  /**
    * Rename an item.
    *
    * @param id    the item's id
    * @param local the item's new local identifier
    * @tparam MT the generic type of the item
    * @return a mapping of old-ID to new-ID for this
    *         item and it's children
    */
  def rename[MT: Resource](id: String, local: String, logMsg: Option[String]): Future[Seq[(String, String)]]

  /**
    * List items with the given resource type.
    *
    * @param resource the resource type
    * @param params   the list parameters
    * @tparam MT the generic type of the resource items
    */
  def list[MT](resource: Resource[MT], params: PageParams): Future[Page[MT]]

  /**
    * List items with the implicit resource type.
    *
    * @param params the list parameters
    * @tparam MT the generic type of the items
    */
  def list[MT: Resource](params: PageParams = PageParams.empty): Future[Page[MT]]

  /**
    * Stream items with the implicit resource type.
    *
    * @tparam MT the generic type of the items
    * @return a Source of items
    */
  def stream[MT: Resource](): Source[MT, _]

  /**
    * List child items of this parent type.
    *
    * @param id     the parent item id
    * @param params the list parameters
    * @param all    fetch full tree (children of children)
    * @tparam MT  the parent generic type
    * @tparam CMT the child generic resource type
    */
  def children[MT: Resource, CMT: Readable](id: String, params: PageParams = PageParams.empty, all: Boolean = false): Future[Page[CMT]]

  /**
    * Fetch child items as a stream.
    *
    * @param id     the parent item id
    * @param params the list parameters
    * @param all    fetch full tree (children of children)
    * @tparam MT  the parent generic type
    * @tparam CMT the child generic resource type
    * @return a Source of child items
    */
  def streamChildren[MT: Resource, CMT: Readable](id: String, params: PageParams = PageParams.empty, all: Boolean = false): Source[CMT, _]

  /**
    * Count child items of a resource.
    *
    * @tparam MT the generic type of the items
    */
  def count[MT: Resource](): Future[Long]

  /**
    * Count an item's child resources.
    *
    * @param id the item's id
    * @tparam MT the generic type of the parent
    */
  def countChildren[MT: Resource](id: String): Future[Long]

  /**
    * Promote an item.
    *
    * @param id the item's id
    * @return the promoted item
    */
  def promote[MT: Resource](id: String): Future[MT]

  /**
    * Remove a promotion.
    *
    * @param id the item's id
    * @return the updated item
    */
  def removePromotion[MT: Resource](id: String): Future[MT]

  /**
    * Demote an item.
    *
    * @param id the item's id
    * @return the updated item
    */
  def demote[MT: Resource](id: String): Future[MT]

  /**
    * Remove a demotion.
    *
    * @param id the item's id
    * @return the updated item
    */
  def removeDemotion[MT: Resource](id: String): Future[MT]

  /**
    * Fetch links for the given item.
    *
    * @param id the item's id
    * @return a page of link items
    */
  def links[A: Readable](id: String): Future[Page[A]]

  /**
    * Fetch annotations for a given item.
    *
    * @param id the item's id
    * @return a page of annotation items
    */
  def annotations[A: Readable](id: String): Future[Page[A]]

  /**
    * List permission grants for a given item.
    *
    * @param id     the item's id
    * @param params the paging parameters
    * @return a page of permission grant items
    */
  def itemPermissionGrants[A: Readable](id: String, params: PageParams): Future[Page[A]]

  /**
    * List permission grants for which this item is the scope.
    *
    * @param id     the item's id
    * @param params the paging parameters
    * @return a page of permission grants
    */
  def scopePermissionGrants[A: Readable](id: String, params: PageParams): Future[Page[A]]

  /**
    * Fetch an item's history.
    *
    * @param id      the item id
    * @param params  range params
    * @param filters event filter params
    */
  def history[A: Readable](id: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]]

  /**
    * Create a new access point on the given item description.
    *
    * @param id     the item's ID
    * @param did    the description ID
    * @param ap     the access point data
    * @param logMsg an optional log message
    * @tparam MT the item's meta type
    * @tparam AP the access point type
    */
  def createAccessPoint[MT: Resource, AP: Writable](id: String, did: String, ap: AP, logMsg: Option[String] = None): Future[AP]

  /**
    * Delete a given access point.
    *
    * @param id     the item's ID
    * @param did    the description ID
    * @param apid   the access point ID
    * @param logMsg an optional log message
    * @tparam MT the item's meta type
    */
  def deleteAccessPoint[MT: Resource](id: String, did: String, apid: String, logMsg: Option[String] = None): Future[Unit]

  /**
    * Fetch a personalised event stream for a given user.
    *
    * @param userId  the user's id
    * @param params  range params
    * @param filters event filter params
    */
  def userEvents[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]]

  /**
    * Fetch a list of events corresponding to a user's actions.
    *
    * @param userId  the user's id
    * @param params  range params
    * @param filters event filter params
    */
  def userActions[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]]

  /**
    * Fetch versions for an item.
    *
    * @param id     the item id
    * @param params range params
    */
  def versions[V: Readable](id: String, params: PageParams): Future[Page[V]]

  /**
    * Create an annotation on an item.
    *
    * @param id        the item id
    * @param ann       the annotation data
    * @param accessors the user's who can access this annotation
    * @param subItem   an optional dependent component ID (e.g. a description) of the item
    * @return the updated item
    */
  def createAnnotation[A <: WithId : Readable, AF: Writable](id: String, ann: AF, accessors: Seq[String] = Nil,
    subItem: Option[String] = None): Future[A]

  /**
    * Create an annotation on a specific description.
    *
    * @param id        the item id
    * @param did       the description id
    * @param ann       the annotation data
    * @param accessors the user's who can access this annotation
    * @return the updated item
    */
  @Deprecated
  def createAnnotationForDependent[A <: WithId : Readable, AF: Writable](id: String, did: String, ann: AF, accessors: Seq[String] = Nil): Future[A]

  /**
    * Create a link on an item.
    *
    * @param id          the source item id
    * @param to          the target item id
    * @param link        the link data
    * @param accessPoint an optional access point id
    * @return the updated item
    */
  def linkItems[MT: Resource, A <: WithId : Readable, AF: Writable](id: String, to: String, link: AF, accessPoint: Option[String] = None, directional: Boolean = false): Future[A]

  /**
    * Create multiple links on an item.
    *
    * @param id         the item id
    * @param srcToLinks a tuple of source item id, link body, and (optional) access point id
    * @return a sequence of the item updated with each link
    */
  def linkMultiple[MT: Resource, A <: WithId : Readable, AF: Writable](id: String, srcToLinks: Seq[(String, AF, Option[String])]): Future[Seq[A]]

  /**
    * Fetch the global event stream.
    *
    * @param params  range params
    * @param filters event filter params
    */
  def events[A: Readable](params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]]

  /**
    * Fetch all subjects for a given event.
    *
    * @param id     the item id
    * @param params range params
    */
  def subjectsForEvent[A: Readable](id: String, params: PageParams): Future[Page[A]]

  /**
    * Include a set of items in a virtual collection.
    *
    * @param vcId the virtual collection id
    * @param ids  a set of item ids
    */
  def addReferences[MT: Resource](vcId: String, ids: Seq[String]): Future[Unit]

  /**
    * Remove a set of items from a virtual collection.
    *
    * @param vcId the virtual collection id
    * @param ids  a set of item ids
    */
  def deleteReferences[MT: Resource](vcId: String, ids: Seq[String]): Future[Unit]

  /**
    * Move a set of items from one virtual collection to another
    *
    * @param fromVc the source virtual collection id
    * @param toVc   the destination virtual collection id
    * @param ids    a set of item ids
    */
  def moveReferences[MT: Resource](fromVc: String, toVc: String, ids: Seq[String]): Future[Unit]

  /**
    * Get a permission set for a particular item.
    *
    * @param userId      the user's id
    * @param contentType the type of the item
    * @param id          the item's id
    * @return an item permission set
    */
  def itemPermissions(userId: String, contentType: ContentTypes.Value, id: String): Future[ItemPermissionSet]

  /**
    * Set a permission set for a particular item.
    *
    * @param userId      the user's id
    * @param contentType the type of the item
    * @param id          the item's id
    * @param data        the new permission data
    * @return an item permission set
    */
  def setItemPermissions(userId: String, contentType: ContentTypes.Value, id: String, data: Seq[String]): Future[ItemPermissionSet]

  /**
    * Set the global permissions for a particular user.
    *
    * @param userId the user's id
    * @param data   the permission data
    * @return a global permission set
    */
  def setGlobalPermissions(userId: String, data: Map[String, Seq[String]]): Future[GlobalPermissionSet]

  /**
    * Get the global permissions for a particular user.
    *
    * @param userId the user's id
    * @return a global permission set
    */
  def globalPermissions(userId: String): Future[GlobalPermissionSet]

  /**
    * Get the permissions for a particular user in a given scope.
    *
    * @param userId the user's id
    * @param id     the scope item's id
    * @return a scoped permission set
    */
  def scopePermissions(userId: String, id: String): Future[GlobalPermissionSet]

  /**
    * Set the permissions for a particular user in a given scope.
    *
    * @param userId the user's id
    * @param id     the scope item's id
    * @param data   the permission data
    * @return a scoped permission set
    */
  def setScopePermissions(userId: String, id: String, data: Map[String, Seq[String]]): Future[GlobalPermissionSet]

  /**
    * List permissions for a particular user.
    *
    * @param userId the user's id
    * @param params the paging parameters
    * @return a page of permissions
    */
  def permissionGrants[A: Readable](userId: String, params: PageParams): Future[Page[A]]

  /**
    * Set item parent(s). This behaviour is dependent on the item type.
    *
    * @param id        the item id
    * @param parentIds a sequence of parent ids
    * @tparam MT  the item's meta model type
    * @tparam PMT the parent item(s) meta model type
    * @return the item
    */
  def parent[MT: Resource, PMT: Resource](id: String, parentIds: Seq[String]): Future[MT]

  /**
    * Add a user to a particular group.
    *
    * @param groupId the group id
    * @param userId  the user's id
    */
  def addGroup[GT: Resource, UT: Resource](groupId: String, userId: String): Future[Unit]

  /**
    * Remove a user from a particular group.
    *
    * @param groupId the group id
    * @param userId  the user's id
    */
  def removeGroup[GT: Resource, UT: Resource](groupId: String, userId: String): Future[Unit]

  /**
    * Follow a user.
    *
    * @param userId  the current user's id
    * @param otherId the other user's id
    */
  def follow[U: Resource](userId: String, otherId: String): Future[Unit]

  /**
    * Unfollow a user.
    *
    * @param userId  the current user's id
    * @param otherId the other user's id
    */
  def unfollow[U: Resource](userId: String, otherId: String): Future[Unit]

  /**
    * Determine if the current user is following another user.
    *
    * @param userId  the current user's id
    * @param otherId the other user's id
    */
  def isFollowing(userId: String, otherId: String): Future[Boolean]

  /**
    * Determine if another user is following the current user.
    *
    * @param userId  the current user's id
    * @param otherId the other user's id
    */
  def isFollower(userId: String, otherId: String): Future[Boolean]

  /**
    * Get a page of this user's followers.
    *
    * @param userId the current user's id
    * @param params the paging parameters
    * @return a page of users
    */
  def followers[U: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[U]]

  /**
    * Get a page of users the current user is following.
    *
    * @param userId the current user's id
    * @param params the paging parameters
    * @return a page of users
    */
  def following[U: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[U]]

  /**
    * Get a page of items the current user is watching.
    *
    * @param userId the current user's id
    * @param params the paging parameters
    * @return a page of items
    */
  def watching[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]]

  /**
    * Start watching an item.
    *
    * @param userId the current user's id
    * @param id     the item's id
    */
  def watch(userId: String, id: String): Future[Unit]

  /**
    * Stop watching an item.
    *
    * @param userId the current user's id
    * @param id     the item's id
    */
  def unwatch(userId: String, id: String): Future[Unit]

  /**
    * Determine if the current user is watching a particular item.
    *
    * @param userId the current user's id
    * @param id     the item's id
    */
  def isWatching(userId: String, id: String): Future[Boolean]

  /**
    * Get a list of users the current user has blocked.
    *
    * @param userId the current user's id
    * @param params the paging params
    * @return a page of user items
    */
  def blocked[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]]

  /**
    * Start blocking a user.
    *
    * @param userId  the current user's id
    * @param otherId the other user's id
    */
  def block(userId: String, otherId: String): Future[Unit]

  /**
    * Stop blocking a user.
    *
    * @param userId  the current user's id
    * @param otherId the other user's id
    */
  def unblock(userId: String, otherId: String): Future[Unit]

  /**
    * Determine if the current user is blocking another user.
    *
    * @param userId  the current user's id
    * @param otherId the other user's id
    */
  def isBlocking(userId: String, otherId: String): Future[Boolean]

  /**
    * Fetch the current user's annotations.
    *
    * @param userId the current user
    * @param params the paging params
    * @return a page of annotation items
    */
  def userAnnotations[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]]

  /**
    * Fetch the current user's links.
    *
    * @param userId the current user
    * @param params the paging params
    * @return a page of link items
    */
  def userLinks[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]]

  /**
    * Fetch the current user's bookmarks (virtual collections).
    *
    * @param userId the current user
    * @param params the paging params
    * @return a page of bookmark items
    */
  def userBookmarks[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]]
}
