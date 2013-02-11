package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import play.api.mvc._
import play.api.data.{ Form, FormError }
import defines.PermissionType
import models.{Entity, UserProfile}
import models.forms.VisibilityForm
import rest.EntityDAO
import play.api.i18n.Messages

/**
 * Controller trait for creating, updating, and deleting auxiliary descriptions
 * for entities that can be multiply described.
 *
 * FIXME: Ultimately there should be REST methods on the server to support
 * these operations so we don't have to transfer so much data back and forth.
 *
 */
trait DescriptionCRUD[T <: AccessibleEntity with DescribedEntity with Formable[F], F <: Persistable, TD <: Formable[FD] with Description, FD <: Persistable] extends EntityRead[T] {

  /*def createDescriptionPostAction(id: String, builder: Entity => DescribedEntity with Formable[T], form: Form[FD])(
      f: Entity => Either[Form[FD],Entity] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) {
      item => implicit userOpt => implicit request =>
        form.bindFromRequest.fold({ ef =>
            f(item)(Left(ef))(userOpt)(request)
        },
        { desc =>
          val doc = builder(item).to.replaceDescription(desc)
          AsyncRest {
            EntityDAO(entityType, userOpt).update(id, doc).map { itemOrErr =>
              itemOrErr.right.map { updated =>
                Redirect(routes.DocumentaryUnits.get(id))
                  .flashing("success" -> Messages("confirmations.itemWasCreated", updated.id))
              }
            }
          }
        })
    }
  }*/
}

