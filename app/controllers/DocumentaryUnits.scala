package controllers

import models.{DocumentaryUnit,DocumentaryUnitRepr}
import models.base.AccessibleEntity
import defines._
import play.api.libs.concurrent.execution.defaultContext
import rest.EntityDAO
import controllers.base.EntityUpdate
import controllers.base.EntityRead
import controllers.base.EntityDelete
import controllers.base.EntityController
import play.api.data.Form
import models.DocumentaryUnitRepr
import models.Entity
import models.Persistable

object DocumentaryUnits extends DocumentaryUnitContext[DocumentaryUnit,DocumentaryUnitRepr]
			with EntityRead[DocumentaryUnit,DocumentaryUnitRepr]
			with EntityUpdate[DocumentaryUnit,DocumentaryUnitRepr]
			with EntityDelete[DocumentaryUnit,DocumentaryUnitRepr] {
  
  val entityType = EntityType.DocumentaryUnit
  val listAction = routes.DocumentaryUnits.list _
  val cancelAction = routes.DocumentaryUnits.get _
  val deleteAction = routes.DocumentaryUnits.deletePost _
  val updateAction = routes.DocumentaryUnits.updatePost _
  val form = forms.DocumentaryUnitForm.form
  val docForm = forms.DocumentaryUnitForm.form
  val showAction = routes.DocumentaryUnits.get _
  val docShowAction = routes.DocumentaryUnits.get _
  val docCreateAction = routes.DocumentaryUnits.docCreatePost _
  val formView = views.html.documentaryUnit.edit.apply _
  val showView = views.html.documentaryUnit.show.apply _
  val listView = views.html.documentaryUnit.list.apply _
  val docFormView = views.html.documentaryUnit.create.apply _
  val deleteView = views.html.delete.apply _
  val builder = DocumentaryUnitRepr

/*  def publishPost(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(entityType, maybeUser.flatMap(_.profile))
          .get(id).map { itemOrErr =>
            itemOrErr.right.map { item =>
              val doc = builder(item)
              AsyncRest {
                EntityDAO(entityType, maybeUser.flatMap(_.profile))
                  .update(id, doc.copy(publicationStatus = Some(defines.PublicationStatus.Published)).toData).map { itemOrErr =>
                    itemOrErr.right.map { item =>
                      Redirect(docShowAction(item.identifier))
                    }
                  }
              }
            }
          }
      }
  }*/
}


/**
 * Controller trait for extending Entity classes which server as
 * context for the creation of DocumentaryUnits, i.e. Agent and
 * DocumentaryUnit itself.
 */
trait DocumentaryUnitContext[F <: Persistable, T <: AccessibleEntity] extends EntityController[F,T] {
  
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
        EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item => Ok(docFormView(builder(item), docForm, docCreateAction(id), maybeUser, request)) }
        }
      }
  }

  def docCreatePost(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      docForm.bindFromRequest.fold(
        errorForm => {
          AsyncRest {
            EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
              itemOrErr.right.map { item =>
                BadRequest(docFormView(builder(item), errorForm, docCreateAction(id), maybeUser, request))
              }
            }
          }
        },
        doc => {
          AsyncRest {
            EntityDAO(entityType, maybeUser.flatMap(_.profile))
              .createInContext(EntityType.DocumentaryUnit, id, doc.toData).map { itemOrErr =>
                itemOrErr.right.map { item =>
                  Redirect(docShowAction(builder(item).identifier))
                }
              }
          }
        }
      )
  }
}


