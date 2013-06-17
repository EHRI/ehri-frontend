package controllers

import play.api.mvc._
import models.{Repository,Country,CountryF,RepositoryF}
import models.forms.{AnnotationForm, VisibilityForm}
import play.api._
import play.api.i18n.Messages
import base._
import defines.{PermissionType, ContentType, EntityType}
import solr.{SearchOrder, SearchParams}

object Countries extends CRUD[CountryF,Country]
  with CreationContext[RepositoryF, Country]
  with VisibilityController[Country]
  with PermissionScopeController[Country]
  with EntityAnnotate[Country]
  with EntitySearch {

  /**
   * Since we generate IDs ourself, set the format.
   */
  final val idFormat = "%06d"

  /**
   * Content types that relate to this controller.
   */
  val targetContentTypes = Seq(ContentType.Repository, ContentType.DocumentaryUnit)

  override def processParams(params: ListParams): rest.RestPageParams = {
    params.toRestParams(Repositories.listFilterMappings, Repositories.orderMappings, Some(Repositories.DEFAULT_SORT))
  }
  override def processChildParams(params: ListParams) = Repositories.processParams(params)

  val entityType = EntityType.Country
  val contentType = ContentType.Country

  val form = models.forms.CountryForm.form
  val childForm = models.forms.RepositoryForm.form

  // Search memebers
  val DEFAULT_SEARCH_PARAMS = SearchParams(entities = List(entityType))


  /**
   * Search repositories inside this country.
   * @param id
   * @return
   */
  def get(id: String) = getAction(id) { item => annotations => links => implicit userOpt => implicit request =>
    searchAction(Map("countryCode" -> item.id), defaultParams = Some(SearchParams(entities = List(EntityType.Repository)))) {
        page => params => facets => _ => _ =>
      Ok(views.html.country.show(Country(item), page, params, facets, routes.Countries.get(id), annotations, links))
    }(request)
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(Country(item), page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.country.list(page.copy(items = page.items.map(Country(_))), params))
  }

  def search = searchAction(defaultParams = Some(DEFAULT_SEARCH_PARAMS)) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(views.html.country.search(page, params, facets, routes.Countries.search))
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.country.create(form, VisibilityForm.form, users, groups, routes.Countries.createPost))
  }

  def createPost = createPostAction(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.country.create(errorForm, accForm, users, groups, routes.Countries.createPost))
      }
      case Right(item) => Redirect(routes.Countries.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.country.edit(
      Country(item), form.fill(Country(item).formable),routes.Countries.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.country.edit(
          Country(olditem), errorForm, routes.Countries.updatePost(id)))
      case Right(item) => Redirect(routes.Countries.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  /**
   * Fetch the existing set of repository ids. Remove the non-numeric (country code)
   * prefix, and increment to form a new id.
   * @type {[type]}
   */
  private def getNextRepositoryId(f: String => Result)(implicit userOpt: Option[models.UserProfile], request: RequestHeader) = {
    import play.api.libs.concurrent.Execution.Implicits._
    import play.api.libs.json.Json
    import play.api.libs.json.JsValue

    def safeInt(s : String) : Option[Int] = try {
      Some(s.toInt)
    } catch {
      case _ : java.lang.NumberFormatException => None
    }

    AsyncRest {
      val allIds = """START n = node:entities("__ISA__:%s") RETURN n.__ID__""".format(EntityType.Repository)
      rest.cypher.CypherDAO(userOpt).cypher(allIds).map { r =>
        r.right.map { json =>
          val result = json.as[Map[String,JsValue]]
          val data: JsValue = result.getOrElse("data", Json.arr())
          val id = data.as[List[List[String]]].flatten.flatMap { rid =>
            rid.split("\\D+").filterNot(_ == "").headOption.flatMap(safeInt)
          }.padTo(1, 0).max + 1 // ensure we get '1' with an empty list

          f(idFormat.format(id))
        }
      }
    }
  }

  def createRepository(id: String) = childCreateAction(id, ContentType.Repository) {
      item => users => groups => implicit userOpt => implicit request =>

    // Beware! This is dubious because there could easily be contention
    // if two repositories get created at the same time.
    // Currently there is not way to notify the user that they should just
    // reset the form or increment the ID manually.
    getNextRepositoryId { newid =>
      val form = childForm.bind(Map("identifier" -> newid))
      Ok(views.html.repository.create(
        Country(item), form, VisibilityForm.form, users, groups, routes.Countries.createRepositoryPost(id)))
    }
  }

  def createRepositoryPost(id: String) = childCreatePostAction(id, childForm, ContentType.Repository) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.repository.create(Country(item),
          errorForm, accForm, users, groups, routes.Countries.createRepositoryPost(id)))
      }
      case Right(citem) => Redirect(routes.Repositories.get(citem.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        Country(item), routes.Countries.deletePost(id),
        routes.Countries.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.Countries.search())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(Country(item),
        models.forms.VisibilityForm.form.fill(Country(item).accessors.map(_.id)),
        users, groups, routes.Countries.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.Countries.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) =
    manageScopedPermissionsAction(id, page, spage, limit) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(Country(item), perms, sperms,
        routes.Countries.addItemPermissions(id), routes.Countries.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(Country(item), users, groups,
        routes.Countries.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(Country(item), users, groups,
        routes.Countries.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(Country(item), accessor, perms, contentType,
        routes.Countries.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(routes.Countries.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(Country(item), accessor, perms, targetContentTypes,
        routes.Countries.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(routes.Countries.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }
}


