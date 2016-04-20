package backend.rest

object Constants {
  /**
   * Name of the header that passes that accessing user id to
   * the server.
   */
  val AUTH_HEADER_NAME = "X-User"

  /**
   * Name of header for passing a log message through a POST request.
   */
  val LOG_MESSAGE_HEADER_NAME = "X-LogMessage"

  /**
   * Indicate that we want to stream list results.
   */
  val STREAM_HEADER_NAME = "X-Stream"

  /**
   * Name of the header for specifying partial item merge (placeholder
   * for lack of explicit HTTP PATCH support.
   */
  val PATCH_HEADER_NAME = "X-Patch"

  /**
   * Parameter for specifying allowed accessors to resources.
   */
  val ACCESSOR_PARAM = "accessibleTo"

  /**
   * Name of header for passing a log message through a POST request.
   */
  val LOG_MESSAGE_PARAM = "logMessage"

  /**
   * Parameter for default group membership.
   */
  val GROUP_PARAM = "group"

  /**
   * Parameter for default group membership.
   */
  val ID_PARAM = "id"

  /**
   * Target parameter for annotations and links.
   */
  val TARGET_PARAM = "target"

  /**
   * Source parameter for annotations and links.
   */
  val SOURCE_PARAM = "source"

  /**
   * Body parameter for annotations and links.
   */
  val BODY_PARAM = "body"

  /**
   * Properties serialization params.
   */
  val INCLUDE_PROPERTIES_PARAM = "_ip"

  /**
   * Time to cache rest requests for...
   */
  import scala.concurrent.duration._
  val cacheTime = (60 * 5).seconds

  /**
   * Limit for lists
   */
  final val LIMIT_PARAM = "limit"

  /**
   * Page for pages
   */
  final val OFFSET_PARAM = "offset"

  /**
   * Default limit
   */
  final val DEFAULT_LIST_LIMIT = 20

  /**
   * Max list limit
   */
  final val MAX_LIST_LIMIT = 100

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
  val DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:SS"

  /**
   * Group members
   */
  val MEMBER = "member"

}
