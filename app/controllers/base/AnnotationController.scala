package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import models.base.Persistable
import defines._
import models.{Annotation, Entity, UserProfile}
import play.api.data.Form
import models.forms.AnnotationF

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam T the entity's build class
 */
trait AnnotationController[T <: AccessibleEntity] extends EntityRead[T] {

  def annotationAction(id: String)(f: Entity => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit user =>
      implicit request =>
      f(item)(user)(request)
    }
  }

  def annotationPostAction(id: String)(f: Either[Form[AnnotationF],Annotation] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit user =>
      implicit request =>
        implicit val maybeUser = Some(user)
        models.forms.AnnotationForm.form.bindFromRequest.fold(
          errorForm => f(Left(errorForm))(user)(request),
          ann => {
            AsyncRest {
              rest.AnnotationDAO(Some(user)).createFor(id, ann).map { annOrErr =>
                annOrErr.right.map { ann =>
                  f(Right(ann))(user)(request)
                }
              }
            }
          }
        )
    }
  }
}

