package backend.rest

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object Constants {
  /**
   * Name of the header that passes that accessing user id to
   * the server.
   */
  val AUTH_HEADER_NAME = "Authorization"

  /**
   * Name of header for passing a log message through a POST request.
   */
  val LOG_MESSAGE_HEADER_NAME = "logMessage"

  /**
   * Name of the header for specifying partial item merge (placeholder
   * for lack of explicit HTTP PATCH support.
   */
  val PATCH_HEADER_NAME = "Patch"

  /**
   * Parameter for specifying allowed accessors to resources.
   */
  val ACCESSOR_PARAM = "accessibleTo"

  /**
   * Parameter for default group membership.
   */
  val GROUP_PARAM = "group"

  /**
   * Properties serialization params.
   */
  val INCLUDE_PROPERTIES_PARAM = "_ip"

  /**
   * Time to cache rest requests for...
   */
  val cacheTime = 60 * 5 // 5 minutes

  /**
   * Offset for lists
   */
  val OFFSET_PARAM = "offset"

  /**
   * Limit for lists
   */
  final val LIMIT_PARAM = "limit"

  /**
   * Page for pages
   */
  final val PAGE_PARAM = "page"

  /**
   * Default limit
   */
  final val DEFAULT_LIST_LIMIT = 20

  /**
   * User filter
   */
  val USERS = "user"

  /**
   * From filter
   */
  val FROM = "from"

  /**
   * To filter
   */
  val TO = "to"

  /**
   * Item type filter
   */
  val ITEM_TYPE = "type"

  /**
   * Event type filter
   */
  val EVENT_TYPE = "et"

  /**
   * Pattern for form input datetime objects
   */
  val DATE_PATTERN = "yyyy-MM-dd"

}
