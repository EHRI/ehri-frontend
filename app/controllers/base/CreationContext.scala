package controllers.base

import play.api.mvc._
import play.api.data.Form
import play.api.libs.concurrent.Execution.Implicits._
import defines.ContentType
import models.base.Persistable
import models.base.AccessibleEntity
import defines.PermissionType
import models.{Entity, UserProfile}

/**
 * Controller trait for extending Entity classes which server as
 * context for the creation of DocumentaryUnits, i.e. Agent and
 * DocumentaryUnit itself.
 */
trait CreationContext[CF <: Persistable, T <: AccessibleEntity] extends EntityRead[T] {

  def childCreateAction(id: String, ct: ContentType.Value)(f: Entity => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Create, ct) { implicit user =>
      implicit request =>
        getEntity(id, Some(user)) { item =>
          f(item)(user)(request)
        }
    }
  }

  def childCreatePostAction[CT<:Persistable](id: String, form: Form[CT], ct: ContentType.Value)(
        f: Either[Form[CT],Entity] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Create, ct) { implicit user =>
      implicit request =>
        implicit val maybeUser = Some(user)

        form.bindFromRequest.fold(
          errorForm => f(Left(errorForm))(user)(request),
          item => {
            AsyncRest {
              rest.EntityDAO(entityType, maybeUser)
                .createInContext(id, item).map { itemOrErr =>
              // If we have an error, check if it's a validation error.
              // If so, we need to merge those errors back into the form
              // and redisplay it...
                if (itemOrErr.isLeft) {
                  itemOrErr.left.get match {
                    case err: rest.ValidationError => {
                      val serverErrors = item.errorsToForm(err.errorSet)
                      val filledForm = form.fill(item).copy(errors = form.errors ++ serverErrors)
                      Right(f(Left(filledForm))(user)(request))
                    }
                    case e => Left(e)
                  }
                } else itemOrErr.right.map {
                  item => f(Right(item))(user)(request)
                }
              }
            }
          }
        )
    }
  }
}
