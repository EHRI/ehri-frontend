package controllers

import play.api.mvc.{Action, Controller}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object GlobalController extends Controller {
  /**
   * Handle trailing slashes with a permanent redirect.
   */
  def untrail(path: String) = Action { request =>
    val query = if (request.rawQueryString != "") "?" + request.rawQueryString else ""
    MovedPermanently("/" + path + query)
  }
}
