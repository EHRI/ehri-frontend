package controllers.archdesc

import _root_.controllers.ListParams
import play.api.mvc._
import forms.VisibilityForm
import controllers.base._
import models._
import play.api._
import play.api.i18n.Messages
import defines.{ContentType, EntityType}
import scala.Some
import utils.search.SearchParams
import utils.search.Dispatcher
import com.google.inject._

@Singleton
class Countries @Inject()(implicit val globalConfig: global.GlobalConfig) extends CRUD[CountryF,Country]
  with CreationContext[RepositoryF, Repository, Country]
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
    searchAction[Repository](Map("countryCode" -> item.id), defaultParams = Some(SearchParams(entities = List(EntityType.Repository)))) {
        page => params => facets => _ => _ =>
      Ok(views.html.country.show(item, page, params, facets, controllers.archdesc.routes.Countries.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.country.list(page, params))
  }

  def search = searchAction[Country](defaultParams = Some(DEFAULT_SEARCH_PARAMS)) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(views.html.country.search(page, params, facets, controllers.archdesc.routes.Countries.search))
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.country.create(form, VisibilityForm.form, users, groups, controllers.archdesc.routes.Countries.createPost))
  }

  def createPost = createPostAction(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.country.create(errorForm, accForm, users, groups, controllers.archdesc.routes.Countries.createPost))
      }
      case Right(item) => Redirect(controllers.archdesc.routes.Countries.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.country.edit(item, form.fill(item.model),controllers.archdesc.routes.Countries.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.country.edit(
          olditem, errorForm, controllers.archdesc.routes.Countries.updatePost(id)))
      case Right(item) => Redirect(controllers.archdesc.routes.Countries.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  /**
   * Fetch the existing set of repository ids. Remove the non-numeric (country code)
   * prefix, and increment to form a new id.
   */
  private def getNextRepositoryId(f: String => Result)(implicit userOpt: Option[UserProfile], request: RequestHeader) = {
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
        item, form, VisibilityForm.form, users, groups, controllers.archdesc.routes.Countries.createRepositoryPost(id)))
    }
  }

  def createRepositoryPost(id: String) = childCreatePostAction(id, childForm, ContentType.Repository) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.repository.create(item,
          errorForm, accForm, users, groups, controllers.archdesc.routes.Countries.createRepositoryPost(id)))
      }
      case Right(citem) => Redirect(controllers.archdesc.routes.Repositories.get(citem.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, controllers.archdesc.routes.Countries.deletePost(id),
        controllers.archdesc.routes.Countries.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.Countries.search())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, controllers.archdesc.routes.Countries.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.Countries.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) =
    manageScopedPermissionsAction(id, page, spage, limit) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        controllers.archdesc.routes.Countries.addItemPermissions(id), controllers.archdesc.routes.Countries.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        controllers.archdesc.routes.Countries.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        controllers.archdesc.routes.Countries.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        controllers.archdesc.routes.Countries.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.Countries.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        controllers.archdesc.routes.Countries.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.Countries.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }
}

