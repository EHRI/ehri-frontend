package controllers

import models.{AccessibleEntity,DocumentaryUnit}
import defines._
import play.api.libs.concurrent.execution.defaultContext
import rest.EntityDAO
import controllers.base.EntityUpdate
import controllers.base.EntityRead
import controllers.base.EntityDelete
import controllers.base.DocumentaryUnitContext


object DocumentaryUnits extends DocumentaryUnitContext[DocumentaryUnit]
			with EntityRead[DocumentaryUnit]
			with EntityUpdate[DocumentaryUnit]
			with EntityDelete[DocumentaryUnit] {
  
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
  val builder: (AccessibleEntity => DocumentaryUnit) = DocumentaryUnit.apply _

  def publishPost(id: String) = userProfileAction { implicit maybeUser =>
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
  }
}


