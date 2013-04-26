package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import models.base.Persistable
import defines._
import models._
import play.api.data.Form
import rest.{RestError, RestPageParams, EntityDAO}
import collection.immutable.ListMap
import controllers.ListParams
import models.forms.{LinkForm, AnnotationForm}
import scala.concurrent.Future


object EntityAnnotate {
  /**
   * Mapping between incoming list filter parameters
   * and the data values accessed via the server.
   */
  val DEFAULT_SORT = AccessibleEntity.NAME

  val listFilterMappings: ListMap[String,String] = ListMap(
    AccessibleEntity.NAME -> s"<-describes.${AccessibleEntity.NAME}"
  )

  val orderMappings: ListMap[String,String] = ListMap(
    AccessibleEntity.NAME -> AccessibleEntity.NAME
  )
}

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam T the entity's build class
 */
trait EntityAnnotate[T <: AnnotatableEntity] extends EntityRead[T] {

  def annotationAction(id: String)(f: models.Entity => Form[AnnotationF] => Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      println("Item: " + item)
      f(item)(AnnotationForm.form.bindFromRequest)(userOpt)(request)
    }
  }

  def annotationPostAction(id: String)(f: Either[Form[AnnotationF],Annotation] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      AnnotationForm.form.bindFromRequest.fold(
        errorForm => f(Left(errorForm))(userOpt)(request),
        ann => {
          AsyncRest {
            rest.AnnotationDAO(userOpt).create(id, ann).map { annOrErr =>
              annOrErr.right.map { ann =>
                f(Right(ann))(userOpt)(request)
              }
            }
          }
        }
      )
    }
  }
}

