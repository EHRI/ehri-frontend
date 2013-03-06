package controllers

import models.{Actor, ActorF}
import models.forms.VisibilityForm
import play.api._
import play.api.i18n.Messages
import defines._
import base._
import play.filters.csrf.CSRF.Token
import collection.immutable.ListMap
import views.Helpers

object Actors extends CRUD[ActorF,Actor]
	with VisibilityController[Actor]
  with PermissionItemController[Actor]
  with EntityAnnotate[Actor]
  with EntitySearch[Actor] {

  val listFilterMappings = ListMap[String,String]()
  val orderMappings = ListMap[String,String]()
  val DEFAULT_SORT = "name"

  // Documentary unit facets
  import solr.facet._
  val entityFacets = List(
    FieldFacetClass(
      key="entityType",
      name=Messages("isaar.entityType"),
      param="cpf",
      render=s => Messages("isaar.entityTypes." + s)
    )
  )

  val searchEntities = List(
    EntityType.ActorDescription,
    EntityType.Actor
  )


  override def processParams(params: ListParams): rest.RestPageParams = {
    params.toRestParams(listFilterMappings, orderMappings, Some(DEFAULT_SORT))
  }
  override def processChildParams(params: ListParams) = DocumentaryUnits.processChildParams(params)


  val targetContentTypes = Seq(ContentType.DocumentaryUnit)

  val entityType = EntityType.Actor
  val contentType = ContentType.Actor

  val form = models.ActorForm.form
  val builder = Actor


  def search = searchAction {
    page => params => facets => implicit userOpt => implicit request =>
      Ok(views.html.search.search(page, params, facets, routes.Actors.search))

  }

  def get(id: String) = getAction(id) {
      item => annotations => implicit userOpt => implicit request =>
    Ok(views.html.actor.show(Actor(item), annotations))
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    // TODO: Add relevant params
    Ok(views.html.systemEvents.itemList(Actor(item), page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.actor.list(page.copy(items = page.items.map(Actor(_))), params))
  }

  def create = createAction {
      users => groups => implicit userOpt => implicit request =>
    Ok(views.html.actor.create(form,
        VisibilityForm.form, users, groups, routes.Actors.createPost))
  }

  def createPost = createPostAction(models.ActorForm.form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.actor.create(errorForm, accForm, users, groups, routes.Actors.createPost))
      }
      case Right(item) => Redirect(routes.Actors.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.actor.edit(Some(Actor(item)), form.fill(Actor(item).formable), routes.Actors.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.actor.edit(Some(Actor(item)), errorForm, routes.Actors.updatePost(id)))
      case Right(item) => Redirect(routes.Actors.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(Actor(item), routes.Actors.deletePost(id),
        routes.Actors.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.Actors.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(Actor(item),
      VisibilityForm.form.fill(Actor(item).accessors.map(_.id)),
      users, groups, routes.Actors.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(routes.Actors.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = manageItemPermissionsAction(id, page, limit) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.managePermissions(Actor(item), perms,
        routes.Actors.addItemPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(Actor(item), users, groups,
        routes.Actors.setItemPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(Actor(item), accessor, perms, contentType,
        routes.Actors.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(routes.Actors.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def annotate(id: String) = withItemPermission(id, PermissionType.Annotate, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.annotation.annotate(Actor(item), models.AnnotationForm.form, routes.Actors.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) {
      formOrAnnotation => implicit userOpt => implicit request =>
    formOrAnnotation match {
      case Left(errorForm) => getEntity(id, userOpt) { item =>
        BadRequest(views.html.annotation.annotate(Actor(item),
          errorForm, routes.Actors.annotatePost(id)))
      }
      case Right(annotation) => {
        Redirect(routes.Actors.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }
}