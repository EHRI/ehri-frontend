package controllers.base

import scala.concurrent.Future
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.libs.concurrent.execution.defaultContext
import rest._
import play.api.mvc.Controller
import play.api.mvc.AsyncResult

trait ControllerHelpers {
  this: Controller with AuthController =>

   /**
   * Wrapper function which takes a promise of either a result
   * or a throwable. If the throwable exists it is handled in
   * an appropriate manner and returned as a AsyncResult
   */
  def AsyncRest(promise: Future[Either[Throwable, Result]])(implicit maybeUser: Option[User], request: RequestHeader): AsyncResult = {
    Async {
      promise.map { respOrErr =>
        respOrErr.fold(
          err => err match {
            case e: PermissionDenied => maybeUser match {
              case Some(user) => Unauthorized(views.html.errors.permissionDenied())
              case None => authenticationFailed(request)
            }
            case e: ItemNotFound => NotFound(views.html.errors.itemNotFound())
            case e: ValidationError => BadRequest(err.toString())
            case e => BadRequest(e.toString())
          },
          resp => resp
        )
      }
    }
  }

  import defines._

  /**
   * Ensure the user has global permissions to perform an operation. 
  */
  def EnsurePermission(perm: PermissionType.Value)(block: Result)(
        implicit maybeUser: Option[User], 
        contentType: ContentType.Value, request: RequestHeader): Result = {

    val r = for { user <- maybeUser ; permissions <- user.permissions } yield {
      if (permissions.has(contentType, perm)) {
        block
      } else {
        Unauthorized(views.html.errors.permissionDenied())
      }
    }
    r.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }

  /**
   * Ensure the user has permissions on a given item to perform an action.
   * This function must have an implicit ItemPermissionSet in scope.
   */
  def EnsureItemPermission(perm: PermissionType.Value)(block: Result)(
        implicit maybeUser: Option[User], maybePerms: Option[models.ItemPermissionSet[_]], 
        contentType: ContentType.Value, request: RequestHeader): Result = {

    val r = for { itemPerms <- maybePerms ; user <- maybeUser ; permissions <- user.permissions } yield {
      if (itemPerms.has(perm)) {
        block
      } else {
        if (permissions.has(contentType, perm)) {
          block
        } else {
          Unauthorized(views.html.errors.permissionDenied())
        }
      }
    }
    r.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }
}