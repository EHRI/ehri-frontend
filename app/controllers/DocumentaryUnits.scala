package controllers

import play.api._
import play.api.mvc._
import base.EntityDelete
import base.EntityRead
import base.EntityUpdate
import base.VisibilityController
import defines.{ ContentType, EntityType }
import models.forms.DocumentaryUnitF
import models.DocumentaryUnit
import base.DocumentaryUnitCreator

object DocumentaryUnits extends DocumentaryUnitCreator[DocumentaryUnitF, DocumentaryUnit]
  with VisibilityController[DocumentaryUnitF, DocumentaryUnit]
  with EntityRead[DocumentaryUnit]
  with EntityUpdate[DocumentaryUnitF, DocumentaryUnit]
  with EntityDelete[DocumentaryUnit] {

  val entityType = EntityType.DocumentaryUnit
  val contentType = ContentType.DocumentaryUnit
  val listAction = routes.DocumentaryUnits.list _
  val cancelAction = routes.DocumentaryUnits.get _
  val deleteAction = routes.DocumentaryUnits.deletePost _
  val updateAction = routes.DocumentaryUnits.updatePost _

  val setVisibilityAction = routes.DocumentaryUnits.visibilityPost _
  val visibilityAction = routes.DocumentaryUnits.visibility _
  val visibilityView = views.html.visibility.apply _

  val form = models.forms.DocumentaryUnitForm.form
  val docForm = models.forms.DocumentaryUnitForm.form
  val showAction = routes.DocumentaryUnits.get _
  val docShowAction = routes.DocumentaryUnits.get _
  val docCreateAction = routes.DocumentaryUnits.docCreatePost _
  val formView = views.html.documentaryUnit.edit.apply _
  val showView = views.html.documentaryUnit.show.apply _
  val listView = views.html.documentaryUnit.list.apply _
  val docFormView = views.html.documentaryUnit.create.apply _
  val deleteView = views.html.delete.apply _
  val builder = DocumentaryUnit
}


