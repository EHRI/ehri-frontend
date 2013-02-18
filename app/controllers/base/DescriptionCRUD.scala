package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import play.api.mvc._
import play.api.data.{ Form, FormError }
import defines.{EntityType, PermissionType}
import models.{Entity, UserProfile}
import models.forms.VisibilityForm
import rest.EntityDAO
import play.api.i18n.Messages

/**
 * Controller trait for creating, updating, and deleting auxiliary descriptions
 * for entities that can be multiply described.
 *
 */
trait DescriptionCRUD[T <: AccessibleEntity with DescribedEntity with Formable[F], F <: Persistable, TD <: Formable[FD] with Description, FD <: Persistable] extends EntityRead[T] {

  /**
   * Create an additional description for the given item.
   * @param id
   * @param descriptionType
   * @param form
   * @param f
   * @return
   */
  def createDescriptionPostAction(id: String, descriptionType: EntityType.Value, form: Form[FD])(
      f: Entity => Either[Form[FD],Entity] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) {
      item => implicit userOpt => implicit request =>
        form.bindFromRequest.fold({ ef =>
            f(item)(Left(ef))(userOpt)(request)
        },
        { desc =>
          AsyncRest {
            EntityDAO(entityType, userOpt).createDescription(id, descriptionType, desc).map { itemOrErr =>
            // If we have an error, check if it's a validation error.
            // If so, we need to merge those errors back into the form
            // and redisplay it...
              if (itemOrErr.isLeft) {
                itemOrErr.left.get match {
                  case err: rest.ValidationError => {
                    val serverErrors: Seq[FormError] = desc.errorsToForm(err.errorSet)
                    val filledForm = form.fill(desc).copy(errors = form.errors ++ serverErrors)
                    Right(f(item)(Left(filledForm))(userOpt)(request))
                  }
                  case e => Left(e)
                }
              } else itemOrErr.right.map { updated =>
                f(item)(Right(updated))(userOpt)(request)
              }
            }
          }
        })
    }
  }

  /**
   * Update an item's description.
   * @param id
   * @param descriptionType
   * @param did
   * @param form
   * @param f
   * @return
   */
  def updateDescriptionPostAction(id: String, descriptionType: EntityType.Value, did: String, form: Form[FD])(
    f: Entity => Either[Form[FD],Entity] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) {
      item => implicit userOpt => implicit request =>
        form.bindFromRequest.fold({ ef =>
          f(item)(Left(ef))(userOpt)(request)
        },
        { desc =>
          AsyncRest {
            EntityDAO(entityType, userOpt).updateDescription(id, descriptionType, did, desc).map { itemOrErr =>
            // If we have an error, check if it's a validation error.
            // If so, we need to merge those errors back into the form
            // and redisplay it...
              if (itemOrErr.isLeft) {
                itemOrErr.left.get match {
                  case err: rest.ValidationError => {
                    val serverErrors: Seq[FormError] = desc.errorsToForm(err.errorSet)
                    val filledForm = form.fill(desc).copy(errors = form.errors ++ serverErrors)
                    Right(f(item)(Left(filledForm))(userOpt)(request))
                  }
                  case e => Left(e)
                }
              } else itemOrErr.right.map { updated =>
                f(item)(Right(updated))(userOpt)(request)
              }
            }
          }
        })
    }
  }

  /**
   * Delete an item's description with the given id.
   * @param id
   * @param descriptionType
   * @param did
   * @param f
   * @return
   */
  def deleteDescriptionPostAction(id: String, descriptionType: EntityType.Value, did: String)(
      f: Entity => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) {
      item => implicit userOpt => implicit request =>
      AsyncRest {
        EntityDAO(entityType, userOpt).deleteDescription(id, descriptionType, did).map { itemOrErr =>
          itemOrErr.right.map { updated =>
            f(updated)(userOpt)(request)
          }
        }
      }
    }
  }

}

