package controllers.base

import defines.EntityType
import models.DocumentaryUnit
import models.DocumentaryUnitRepr
import models.base.Persistable
import models.base.AccessibleEntity
import play.api.data.Form
import play.api.libs.concurrent.execution.defaultContext
import play.api.mvc.Call
import play.api.mvc.RequestHeader
import defines.PermissionType

/**
 * Controller trait for extending Entity classes which server as
 * context for the creation of DocumentaryUnits, i.e. Agent and
 * DocumentaryUnit itself.
 */
trait DocumentaryUnitCreator[F <: Persistable, T <: AccessibleEntity] extends EntityController[T] {

  import play.api.mvc.Call
  import play.api.mvc.RequestHeader
  type DocFormViewType = (T, Form[DocumentaryUnit], Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val docFormView: DocFormViewType
  val docForm: Form[DocumentaryUnit]
  val docShowAction: String => Call
  val docCreateAction: String => Call

  def docCreate(id: String) = withItemPermission(id, PermissionType.Create) { implicit maybeUser =>
    implicit maybePerms =>
      implicit request =>
        AsyncRest {
          rest.EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
            itemOrErr.right.map { item => Ok(docFormView(builder(item), docForm, docCreateAction(id), maybeUser, request)) }
          }
        }
  }

  def docCreatePost(id: String) = withItemPermission(id, PermissionType.Create) { implicit maybeUser =>
    implicit maybePerms =>
      implicit request =>
        docForm.bindFromRequest.fold(
          errorForm => {
            AsyncRest {
              rest.EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
                itemOrErr.right.map { item =>
                  BadRequest(docFormView(builder(item), errorForm, docCreateAction(id), maybeUser, request))
                }
              }
            }
          },
          doc => {
            AsyncRest {
              rest.EntityDAO(entityType, maybeUser.flatMap(_.profile))
                .createInContext(EntityType.DocumentaryUnit, id, doc.toData).map { itemOrErr =>
                  // If we have an error, check if it's a validation error.
                  // If so, we need to merge those errors back into the form
                  // and redisplay it...
                  if (itemOrErr.isLeft) {
                    itemOrErr.left.get match {
                      case err: rest.ValidationError => {
                        val serverErrors = doc.errorsToForm(err.errorSet)
                        val filledForm = docForm.fill(doc).copy(errors = docForm.errors ++ serverErrors)
                        val form = AsyncRest {
                          rest.EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
                            itemOrErr.right.map { item =>
                              BadRequest(docFormView(builder(item), filledForm, docCreateAction(id), maybeUser, request))
                            }
                          }
                        }
                        Right(form)
                      }
                      case e => Left(e)
                    }
                  } else itemOrErr.right.map { item => Redirect(docShowAction(item.id)) }
                }
            }
          }
        )
  }
}
