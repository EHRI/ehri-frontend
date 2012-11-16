package controllers.base

import play.api.libs.concurrent.execution.defaultContext
import models.base.AccessibleEntity
import play.api.mvc.RequestHeader
import play.api.mvc.Call
import models.base.Persistable
import play.api.data.Form

trait EntityCreate[F <: Persistable, T <: AccessibleEntity] extends EntityRead[T] {
  type FormViewType = (Option[T], Form[F], Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val createAction: Call
  val formView: FormViewType
  val form: Form[F]

  def create = userProfileAction { implicit maybeUser =>
    implicit request =>
      Ok(formView(None, form, createAction, maybeUser, request))
  }

  def createPost = userProfileAction { implicit maybeUser =>
    implicit request =>
      form.bindFromRequest.fold(
        errorForm => BadRequest(formView(None, errorForm, createAction, maybeUser, request)),
        doc => {
          AsyncRest {
            rest.EntityDAO(entityType, maybeUser.flatMap(_.profile))
              .create(doc.toData).map { itemOrErr =>
                itemOrErr.right.map { item => Redirect(showAction(builder(item).identifier)) }
              }
          }
        }
      )
  }
}

