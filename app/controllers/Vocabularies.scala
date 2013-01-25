package controllers

import models.{ItemWithId,Concept,Vocabulary}
import _root_.models.forms.{VisibilityForm, ConceptF, VocabularyF}
import play.api._
import play.api.i18n.Messages
import base._
import defines.{PermissionType, ContentType, EntityType}

object Vocabularies extends CreationContext[ConceptF, Vocabulary]
  with VisibilityController[Vocabulary]
  with EntityRead[Vocabulary]
  with EntityCreate[VocabularyF,Vocabulary]
  with EntityUpdate[VocabularyF, Vocabulary]
  with EntityDelete[Vocabulary]
  with PermissionScopeController[Vocabulary]
  with AnnotationController[Vocabulary] {

  val targetContentTypes = Seq(ContentType.Concept)

  val entityType = EntityType.Vocabulary
  val contentType = ContentType.Vocabulary

  val form = models.forms.VocabularyForm.form
  val childForm = models.forms.ConceptForm.form
  val builder = Vocabulary.apply _

  def get(id: String) = getAction(id) { item => annotations =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.vocabulary.show(Vocabulary(item), annotations))
  }

  def list(page: Int = 1, limit: Int = DEFAULT_LIMIT) = listAction(page, limit) { page =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.vocabulary.list(page.copy(list = page.list.map(Vocabulary(_)))))
  }

  def create = createAction { users => groups => implicit user =>
    implicit request =>
      Ok(views.html.vocabulary.create(form, VisibilityForm.form, users, groups, routes.Vocabularies.createPost))
  }

  def createPost = createPostAction(models.forms.VocabularyForm.form) { formsOrItem =>
    implicit user =>
      implicit request =>
        formsOrItem match {
          case Left((errorForm,accForm)) => getGroups(Some(user)) { users => groups =>
            BadRequest(views.html.vocabulary.create(errorForm, accForm, users, groups, routes.Vocabularies.createPost))
          }
          case Right(item) => Redirect(routes.Vocabularies.get(item.id))
            .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
        }
  }

  def update(id: String) = updateAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.vocabulary.edit(
        Some(Vocabulary(item)), form.fill(Vocabulary(item).to),routes.Vocabularies.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { olditem => formOrItem =>
    implicit user =>
      implicit request =>
        formOrItem match {
          case Left(errorForm) => BadRequest(views.html.vocabulary.edit(
              Some(Vocabulary(olditem)), errorForm, routes.Vocabularies.updatePost(id)))
          case Right(item) => Redirect(routes.Vocabularies.get(item.id))
            .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
        }
  }

  def createConcept(id: String) = childCreateAction(id, ContentType.Concept) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.concept.create(
        Vocabulary(item), childForm, VisibilityForm.form, users, groups, routes.Vocabularies.createConceptPost(id)))
  }

  def createConceptPost(id: String) = childCreatePostAction(id, childForm, ContentType.Concept) { item => formsOrItem =>
    implicit user =>
      implicit request =>
        formsOrItem match {
          case Left((errorForm,accForm)) => getGroups(Some(user)) { users => groups =>
            BadRequest(views.html.concept.create(Vocabulary(item),
              errorForm, accForm, users, groups, routes.Vocabularies.createConceptPost(id)))
          }
          case Right(citem) => Redirect(routes.Concepts.get(citem.id))
            .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
        }
  }

  def delete(id: String) = deleteAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.delete(
        Vocabulary(item), routes.Vocabularies.deletePost(id),
        routes.Vocabularies.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.Vocabularies.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.permissions.visibility(Vocabulary(item),
        models.forms.VisibilityForm.form.fill(Vocabulary(item).accessors.map(_.id)),
        users, groups, routes.Vocabularies.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.Vocabularies.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) =
    manageScopedPermissionsAction(id, page, spage, limit) {
    item => perms => sperms => implicit user => implicit request =>
      Ok(views.html.permissions.manageScopedPermissions(Vocabulary(item), perms, sperms,
        routes.Vocabularies.addItemPermissions(id), routes.Vocabularies.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(Vocabulary(item), users, groups,
        routes.Vocabularies.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(Vocabulary(item), users, groups,
        routes.Vocabularies.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionItem(Vocabulary(item), accessor, perms, contentType,
        routes.Vocabularies.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
    bool => implicit user => implicit request =>
      Redirect(routes.Vocabularies.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionScope(Vocabulary(item), accessor, perms, targetContentTypes,
        routes.Vocabularies.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
    perms => implicit user => implicit request =>
      Redirect(routes.Vocabularies.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def annotate(id: String) = withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit user =>
    implicit request =>
      Ok(views.html.annotation.annotate(Vocabulary(item), models.forms.AnnotationForm.form, routes.Vocabularies.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) { formOrAnnotation => implicit user =>
    implicit request =>
    formOrAnnotation match {
      case Left(errorForm) => getEntity(id, Some(user)) { item =>
        BadRequest(views.html.annotation.annotate(Vocabulary(item),
            errorForm, routes.Vocabularies.annotatePost(id)))
      }
      case Right(annotation) => {
        Redirect(routes.Vocabularies.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }

  def linkAnnotate(id: String, src: String) = withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit user =>
    implicit request =>
      getEntity(id, Some(user)) { srcitem =>
        Ok(views.html.annotation.linkAnnotate(Vocabulary(item),
          ItemWithId(srcitem),
          models.forms.AnnotationForm.form, routes.Vocabularies.linkAnnotatePost(id, src)))
      }
  }

  def linkAnnotatePost(id: String, src: String) = linkPostAction(id, src) { formOrAnnotation => implicit user =>
    implicit request =>
      formOrAnnotation match {
        case Left(errorForm) => getEntity(id, Some(user)) { item =>
          getEntity(src, Some(user)) { srcitem =>
            BadRequest(views.html.annotation.linkAnnotate(Vocabulary(item), ItemWithId(srcitem),
              errorForm, routes.Vocabularies.linkAnnotatePost(id, src)))
          }
        }
        case Right(annotation) => {
          Redirect(routes.Vocabularies.get(id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
        }
      }
  }

}


