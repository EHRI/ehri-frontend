package controllers.base

import defines.EntityType
import models.DocumentaryUnit
import models.DocumentaryUnitRepr
import models.Persistable
import models.base.AccessibleEntity
import play.api.data.Form
import play.api.libs.concurrent.execution.defaultContext
import play.api.mvc.Call
import play.api.mvc.RequestHeader

/**
 * Controller trait for extending Entity classes which server as
 * context for the creation of DocumentaryUnits, i.e. Agent and
 * DocumentaryUnit itself.
 */
trait DocumentaryUnitCreator[F <: Persistable, T <: AccessibleEntity] extends EntityController[F,T] {
  
  import play.api.mvc.Call
  import play.api.mvc.RequestHeader
  type DocFormViewType = (T, Form[DocumentaryUnit], Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val docFormView: DocFormViewType
  val docForm: Form[DocumentaryUnit]
  val docShowAction: String => Call
  val docCreateAction: String => Call

  def docCreate(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item => Ok(docFormView(builder(item), docForm, docCreateAction(id), maybeUser, request)) }
        }
      }
  }

  def docCreatePost(id: String) = userProfileAction { implicit maybeUser =>
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
                itemOrErr.right.map { item =>
                  Redirect(docShowAction(DocumentaryUnitRepr(item).identifier))
                }
              }
          }
        }
      )
  }
}
