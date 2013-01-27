package controllers

import play.api.libs.concurrent.Execution.Implicits._
import models.{ItemWithId,Concept}
import models.forms.{ConceptF,VisibilityForm}
import play.api._
import play.api.i18n.Messages
import base._
import defines.{PermissionType, ContentType, EntityType}

object Concepts extends CreationContext[ConceptF, Concept]
  with VisibilityController[Concept]
  with EntityRead[Concept]
  with EntityUpdate[ConceptF, Concept]
  with EntityDelete[Concept]
  with PermissionScopeController[Concept]
  with AnnotationController[Concept] {

  val targetContentTypes = Seq(ContentType.Concept)

  val entityType = EntityType.Concept
  val contentType = ContentType.Concept

  val form = models.forms.ConceptForm.form
  val childForm = models.forms.ConceptForm.form
  val builder = Concept.apply _

  def get(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = getWithChildrenAction(id, builder, page, limit) { item => page => annotations =>
    implicit maybeUser => implicit request =>
      Ok(views.html.concept.show(Concept(item), page, annotations))
  }

  def list(page: Int = 1, limit: Int = DEFAULT_LIMIT) = listAction(page, limit) { page =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.concept.list(page.copy(list = page.list.map(Concept(_)))))
  }

  def update(id: String) = updateAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.concept.edit(
        Some(Concept(item)), form.fill(Concept(item).to),routes.Concepts.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { olditem => formOrItem =>
    implicit user =>
      implicit request =>
        formOrItem match {
          case Left(errorForm) => println(errorForm.errors);BadRequest(views.html.concept.edit(
              Some(Concept(olditem)), errorForm, routes.Concepts.updatePost(id)))
          case Right(item) => Redirect(routes.Concepts.get(item.id))
            .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
        }
  }

  def createConcept(id: String) = childCreateAction(id, ContentType.Concept) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.concept.create(
        Concept(item), childForm, VisibilityForm.form, users, groups, routes.Concepts.createConceptPost(id)))
  }

  def createConceptPost(id: String) = childCreatePostAction(id, childForm, ContentType.Concept) { item => formsOrItem =>
    implicit user =>
      implicit request =>
        formsOrItem match {
          case Left((errorForm,accForm)) => getGroups(Some(user)) { users => groups =>
            BadRequest(views.html.concept.create(Concept(item),
              errorForm, accForm, users, groups, routes.Concepts.createConceptPost(id)))
          }
          case Right(citem) => Redirect(routes.Concepts.get(id))
            .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
        }
  }

  def delete(id: String) = deleteAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.delete(
        Concept(item), routes.Concepts.deletePost(id),
        routes.Concepts.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.Concepts.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.permissions.visibility(Concept(item),
        models.forms.VisibilityForm.form.fill(Concept(item).accessors.map(_.id)),
        users, groups, routes.Concepts.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.Concepts.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) =
    manageScopedPermissionsAction(id, page, spage, limit) {
    item => perms => sperms => implicit user => implicit request =>
      Ok(views.html.permissions.manageScopedPermissions(Concept(item), perms, sperms,
        routes.Concepts.addItemPermissions(id), routes.Concepts.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(Concept(item), users, groups,
        routes.Concepts.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(Concept(item), users, groups,
        routes.Concepts.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionItem(Concept(item), accessor, perms, contentType,
        routes.Concepts.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
    bool => implicit user => implicit request =>
      Redirect(routes.Concepts.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionScope(Concept(item), accessor, perms, targetContentTypes,
        routes.Concepts.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
    perms => implicit user => implicit request =>
      Redirect(routes.Concepts.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def annotate(id: String) = withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit user =>
    implicit request =>
      Ok(views.html.annotation.annotate(Concept(item), models.forms.AnnotationForm.form, routes.Concepts.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) { formOrAnnotation => implicit user =>
    implicit request =>
    formOrAnnotation match {
      case Left(errorForm) => getEntity(id, Some(user)) { item =>
        BadRequest(views.html.annotation.annotate(Concept(item),
            errorForm, routes.Concepts.annotatePost(id)))
      }
      case Right(annotation) => {
        Redirect(routes.Concepts.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }

  def linkAnnotate(id: String, src: String) = withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit user =>
    implicit request =>
      getEntity(id, Some(user)) { srcitem =>
        Ok(views.html.annotation.linkAnnotate(Concept(item),
          ItemWithId(srcitem),
          models.forms.AnnotationForm.form, routes.Concepts.linkAnnotatePost(id, src)))
      }
  }

  def linkAnnotatePost(id: String, src: String) = linkPostAction(id, src) { formOrAnnotation => implicit user =>
    implicit request =>
      formOrAnnotation match {
        case Left(errorForm) => getEntity(id, Some(user)) { item =>
          getEntity(src, Some(user)) { srcitem =>
            BadRequest(views.html.annotation.linkAnnotate(Concept(item), ItemWithId(srcitem),
              errorForm, routes.Concepts.linkAnnotatePost(id, src)))
          }
        }
        case Right(annotation) => {
          Redirect(routes.Concepts.get(id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
        }
      }
  }

}


