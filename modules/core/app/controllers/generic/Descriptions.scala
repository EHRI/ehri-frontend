package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import play.api.mvc._
import play.api.data.Form
import defines.{EntityType, PermissionType}
import models.UserProfile
import models.json.{RestReadable, RestConvertable}
import scala.concurrent.Future.{successful => immediate}
import backend.rest.ValidationError

/**
 * Controller trait for creating, updating, and deleting auxiliary descriptions
 * for entities that can be multiply described.
 *
 */
trait Descriptions[D <: Description with Persistable, T <: Model with Described[D], MT <: MetaModel[T]] extends Read[MT] {

  /**
   * Create an additional description for the given item.
   */
  def createDescriptionPostAction(id: String, descriptionType: EntityType.Value, form: Form[D])(
      f: MT => Either[Form[D], MT] => Option[UserProfile] => Request[AnyContent] => Result)(
        implicit fmt: RestConvertable[D], rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update, contentType) {
        item => implicit userOpt => implicit request =>
      form.bindFromRequest.fold(
        ef => immediate(f(item)(Left(ef))(userOpt)(request)),
        desc => backend.createDescription(id, desc, logMsg = getLogMessage).map { updated =>
          f(item)(Right(updated))(userOpt)(request)
        } recoverWith {
          case ValidationError(errorSet) => {
            val badForm = desc.getFormErrors(errorSet, form.fill(desc))
            immediate(f(item)(Left(badForm))(userOpt)(request))
          }
        }
      )
    }
  }

  /**
   * Update an item's description.
   */
  def updateDescriptionPostAction(id: String, descriptionType: EntityType.Value, did: String, form: Form[D])(
    f: MT => Either[Form[D],MT] => Option[UserProfile] => Request[AnyContent] => Result)(
           implicit fmt: RestConvertable[D], rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update, contentType) {
        item => implicit userOpt => implicit request =>
      form.bindFromRequest.fold(
        ef => immediate(f(item)(Left(ef))(userOpt)(request)),
        desc => backend.updateDescription(id, did, desc, logMsg = getLogMessage).map { updated =>
          f(item)(Right(updated))(userOpt)(request)
        } recoverWith {
          case ValidationError(errorSet) => {
            val badForm = desc.getFormErrors(errorSet, form.fill(desc))
            immediate(f(item)(Left(badForm))(userOpt)(request))
          }
        }
      )
    }
  }

  def deleteDescriptionAction(id: String, did: String)(
      f: MT => D => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
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
      f: Boolean => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update, contentType) {
        item => implicit userOpt => implicit request =>
      backend.deleteDescription(id, did, logMsg = getLogMessage).map { ok =>
        f(ok)(userOpt)(request)
      }
    }
  }
}

