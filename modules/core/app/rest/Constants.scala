package rest

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
   * Parameter for specifying allowed accessors to resources.
   */
  val ACCESSOR_PARAM = "accessibleTo"

  /**
   * Time to cache rest requests for...
   */
  val cacheTime = 60 * 5 // 5 minutes


}
