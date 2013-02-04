package controllers.base

import scala.concurrent.Future
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.libs.concurrent.Execution.Implicits._
import rest._
import play.api.mvc.Controller
import play.api.mvc.AsyncResult
import java.net.ConnectException

object ControllerHelpers {
  def isAjax(implicit request: RequestHeader): Boolean =
    request.headers.get("X-REQUESTED-WITH")
      .map(_.toUpperCase() == "XMLHTTPREQUEST").getOrElse(false)
}

trait ControllerHelpers {
  this: Controller with AuthController =>

  def Secured(res: Result)(implicit maybeUser: Option[models.UserProfile], request: RequestHeader): Result = {
    maybeUser.map(u => res).getOrElse(authenticationFailed(request))
  }

   /**
   * Wrapper function which takes a promise of either a result
   * or a throwable. If the throwable exists it is handled in
   * an appropriate manner and returned as a AsyncResult
   */
  def AsyncRest(promise: Future[Either[Throwable, Result]])(implicit maybeUser: Option[models.UserProfile], request: RequestHeader): AsyncResult = {
    Async {
      promise.map { respOrErr =>
        respOrErr.fold(
          err => err match {
            // TODO: Rethink whether we want to redirect here?  All our
            // actions should already be permission-secure, so it's really
            // an error if the server denies permission for something.
            case e: PermissionDenied => maybeUser.map { user =>
              Unauthorized(views.html.errors.permissionDenied())
            } getOrElse {
              authenticationFailed(request)
            }
            case e: ItemNotFound => NotFound(views.html.errors.itemNotFound())
            case e: ValidationError => BadRequest(err.toString())
            case e: ServerError => InternalServerError(views.html.errors.serverTimeout())
            case e => BadRequest(e.toString())
          },
          resp => resp
        )
      } recover {
        case e: ConnectException => InternalServerError(views.html.errors.serverTimeout())
      }
    }
  }

  /*
   * Play Forms don't currently support multi-value select widgets. We
   * need to transform the input from:
   *  key -> Seq(va1, val2, val3) to:
   *  key[0] -> Seq(val1), key[1] -> Seq(val2), key[2] -> Seq(val3)
   */
  def fixMultiSelects(formData: Option[Map[String,Seq[String]]], multi: String*) = {
    formData.map(b => {
      b.flatMap { (t: (String,Seq[String])) =>
        t match {
          case (n, s) if multi.contains(n) => {
            s.zipWithIndex.map(t => n + "[" + t._2 + "]" -> List(t._1))
          }
          case other => List(other)
        }
      }
    }).getOrElse(Map[String,Seq[String]]())
  }
}