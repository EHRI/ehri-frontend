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
trait EntityAnnotate[T <: AccessibleEntity] extends EntityRead[T] {

  def annotationPostAction(id: String)(f: Either[Form[AnnotationF],Annotation] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit user =>
      implicit request =>
        implicit val maybeUser = Some(user)
        models.forms.AnnotationForm.form.bindFromRequest.fold(
          errorForm => f(Left(errorForm))(user)(request),
          ann => {
            AsyncRest {
              rest.AnnotationDAO(Some(user)).create(id, ann).map { annOrErr =>
                annOrErr.right.map { ann =>
                  f(Right(ann))(user)(request)
                }
              }
            }
          }
        )
    }
  }

  def linkAction(id: String, toType: String, to: String)(f: AnnotatableEntity => AnnotatableEntity => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit user => implicit request =>
      implicit val maybeUser = Some(user)
      getEntity(EntityType.withName(toType), to, Some(user)) { srcitem =>
        // If neither items are annotatable throw a 404
        val res: Option[Result] = for {
          target <- AnnotatableEntity.fromEntity(item)
          source <- AnnotatableEntity.fromEntity(srcitem)
        } yield {
            f(target)(source)(user)(request)
        }
        res.getOrElse(NotFound(views.html.errors.itemNotFound()))
      }
    }
  }



  def linkPostAction(id: String, toType: String, to: String)(f: Either[(AnnotatableEntity,AnnotatableEntity,Form[AnnotationF]),Annotation] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit user =>
      implicit request =>
        implicit val maybeUser = Some(user)
        models.forms.AnnotationForm.form.bindFromRequest.fold(
          errorForm => { // oh dear, we have an error...
            getEntity(EntityType.withName(toType), to, Some(user)) { srcitem =>
            // If neither items are annotatable throw a 404
              val res: Option[Result] = for {
                target <- AnnotatableEntity.fromEntity(item)
                source <- AnnotatableEntity.fromEntity(srcitem)
              } yield {
                f(Left((target,source,errorForm)))(user)(request)
              }
              res.getOrElse(NotFound(views.html.errors.itemNotFound()))
            }
          },
          ann => {
            AsyncRest {
              rest.AnnotationDAO(Some(user)).link(id, to, ann).map { annOrErr =>
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

