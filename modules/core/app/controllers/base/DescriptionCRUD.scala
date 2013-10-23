package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import play.api.mvc._
import play.api.data.Form
import defines.{EntityType, PermissionType}
import models.UserProfile
import rest.{DescriptionDAO, ValidationError}
import models.json.{RestReadable, RestConvertable}
import scala.concurrent.Future.{successful => immediate}

/**
 * Controller trait for creating, updating, and deleting auxiliary descriptions
 * for entities that can be multiply described.
 *
 */
trait DescriptionCRUD[D <: Description with Persistable, T <: Model with Described[D], MT <: MetaModel[T]] extends EntityRead[MT] {

  /**
   * Create an additional description for the given item.
   */
  def createDescriptionPostAction(id: String, descriptionType: EntityType.Value, form: Form[D])(
      f: MT => Either[Form[D], MT] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
        implicit fmt: RestConvertable[D], rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update, contentType) {
        item => implicit userOpt => implicit request =>
      form.bindFromRequest.fold({ ef =>
          immediate(f(item)(Left(ef))(userOpt)(request))
      },
      { desc =>
        AsyncRest {
          DescriptionDAO[MT](entityType, userOpt).createDescription(id, desc, logMsg = getLogMessage).map { itemOrErr =>
            if (itemOrErr.isLeft) {
              itemOrErr.left.get match {
                case err: rest.ValidationError => {
                  Right(f(item)(Left(fillFormErrors(desc, form, err)))(userOpt)(request))
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
   */
  def updateDescriptionPostAction(id: String, descriptionType: EntityType.Value, did: String, form: Form[D])(
    f: MT => Either[Form[D],MT] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
           implicit fmt: RestConvertable[D], rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update, contentType) {
        item => implicit userOpt => implicit request =>
      form.bindFromRequest.fold({ ef =>
        immediate(f(item)(Left(ef))(userOpt)(request))
      },
      { desc =>
        AsyncRest {
          DescriptionDAO[MT](entityType, userOpt)
              .updateDescription(id, did, desc, logMsg = getLogMessage).map { itemOrErr =>
            if (itemOrErr.isLeft) {
              itemOrErr.left.get match {
                case err: rest.ValidationError => {
                  Right(f(item)(Left(fillFormErrors(desc, form, err)))(userOpt)(request))
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

  def deleteDescriptionAction(id: String, did: String)(
      f: MT => D => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Update, contentType) {
        item => implicit userOpt => implicit request =>
      item.model.description(did).map { desc =>
        f(item)(desc)(userOpt)(request)
      }.getOrElse {
        NotFound(views.html.errors.itemNotFound(Some(did)))
      }
    }
  }

  /**
   * Delete an item's description with the given id.
   */
  def deleteDescriptionPostAction(id: String, descriptionType: EntityType.Value, did: String)(
      f: Boolean => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update, contentType) {
        item => implicit userOpt => implicit request =>
      AsyncRest {
        DescriptionDAO[MT](entityType, userOpt)
            .deleteDescription(id, did, logMsg = getLogMessage).map { itemOrErr =>
          itemOrErr.right.map { ok =>
            f(ok)(userOpt)(request)
          }
        }
      }
    }
  }

  /**
   * Given a ValidationError from the server and an item, fold
   * the server's complaints back into the form for redisplay.
   * @param item
   * @param form
   * @param err
   * @return
   */
  private def fillFormErrors(item: D, form: Form[D], err: ValidationError): Form[D] = {
    form.fill(item).copy(errors = form.errors ++ item.errorsToForm(err.errorSet))
  }
}

