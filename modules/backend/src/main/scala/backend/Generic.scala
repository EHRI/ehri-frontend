package backend

import defines.ContentTypes
import play.api.libs.json.JsObject
import utils.{Page, PageParams}

import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Generic {

  /**
   * Get an item with an explicit resource type and id.
   *
   * @param resource the resource type
   * @param id the item's id
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
   * @param item the item data
   * @param accessors the users/groups that can initially
   *                  access this item (none implies all)
   * @param params additional web service parameters
   * @param logMsg the log message
   * @tparam MT the generic type of the resource
   * @tparam T the generic type of the resource's data
   */
  def create[MT <: WithId : Resource, T: Writable](item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[MT]

  /**
   * Create a new item in the context of a parent item.
   *
   * @param id the parent's id
   * @param contentType the child content type
   * @param item the new item's data
   * @param accessors the users/groups that can initially
   *                  access this item (none implies all)
   * @param params additional web service parameters
   * @param logMsg the log message
   * @tparam MT the generic type of the parent resource
   * @tparam T the generic type of the child item's data
   * @tparam CMT the generic type of the child item
   */
  def createInContext[MT: Resource, T: Writable, CMT <: WithId : Readable](id: String, contentType: ContentTypes.Value, item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map(), logMsg: Option[String] = None): Future[CMT]

  /**
   * Update an item.
   *
   * @param id the item's id
   * @param item the item's data
   * @param logMsg the log message
   * @tparam MT the generic type of the item
   * @tparam T the generic type of the item's data
   */
  def update[MT: Resource, T: Writable](id: String, item: T, logMsg: Option[String] = None): Future[MT]

  /**
   * Partially update (patch) an item's properties.
   *
   * @param id the item's id
   * @param data a JSON object contain the properties to be
   *             updated
   * @param logMsg the log message
   * @tparam MT the generic type of the item
   */
  def patch[MT: Resource](id: String, data: JsObject, logMsg: Option[String] = None): Future[MT]

  /**
   * Delete an item.
   *
   * @param id the item's id
   * @param logMsg the log message
   * @tparam MT the generic type of the item
   */
  def delete[MT: Resource](id: String, logMsg: Option[String] = None): Future[Unit]

  /**
   * List items with the given resource type.
   *
   * @param resource the resource type
   * @param params the list parameters
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
   * List child items of this parent type.
   *
   * @param id the parent item id
   * @param params the list parameters
   * @tparam MT the parent generic type
   * @tparam CMT the child generic resource type
   */
  def listChildren[MT: Resource, CMT: Readable](id: String, params: PageParams = PageParams.empty): Future[Page[CMT]]

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
}
