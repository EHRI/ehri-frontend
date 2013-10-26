package controllers.archdesc

import play.api.mvc._
import forms.VisibilityForm
import controllers.base._
import models._
import play.api._
import play.api.i18n.Messages
import defines.{ContentTypes, EntityType}
import scala.Some
import utils.search.SearchParams
import utils.search.Dispatcher
import com.google.inject._
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.Future

@Singleton
class Countries @Inject()(implicit val globalConfig: global.GlobalConfig, val searchDispatcher: Dispatcher) extends CRUD[CountryF,Country]
  with CreationContext[RepositoryF, Repository, Country]
  with VisibilityController[Country]
  with PermissionScopeController[Country]
  with EntityAnnotate[Country]
  with EntitySearch {

  /**
   * Since we generate repository IDs ourselves, set the format.
   */
  private final val repoIdFormat = "%06d"

  /**
   * Content types that relate to this controller.
   */
  val targetContentTypes = Seq(ContentTypes.Repository, ContentTypes.DocumentaryUnit)

  val entityType = EntityType.Country
  val contentType = ContentTypes.Country

  val form = models.forms.CountryForm.form
  val childForm = models.forms.RepositoryForm.form

  // Search memebers
  val DEFAULT_SEARCH_PARAMS = SearchParams(entities = List(entityType))

  private final val countryRoutes = controllers.archdesc.routes.Countries

  /**
   * Search repositories inside this country.
   * @param id
   * @return
   */
  def get(id: String) = getAction.async(id) { item => annotations => links => implicit userOpt => implicit request =>
    searchAction[Repository](Map("countryCode" -> item.id), defaultParams = Some(SearchParams(entities = List(EntityType.Repository)))) {
        page => params => facets => _ => _ =>
      Ok(views.html.country.show(item, page, params, facets, countryRoutes.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.country.list(page, params))
  }

  def search = searchAction[Country](defaultParams = Some(DEFAULT_SEARCH_PARAMS)) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(views.html.country.search(page, params, facets, countryRoutes.search))
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.country.create(form, VisibilityForm.form, users, groups, countryRoutes.createPost))
  }

  def createPost = createPostAction.async(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.country.create(errorForm, accForm, users, groups, countryRoutes.createPost))
      }
      case Right(item) => immediate(Redirect(countryRoutes.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id)))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.country.edit(item, form.fill(item.model),countryRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.country.edit(
          olditem, errorForm, countryRoutes.updatePost(id)))
      case Right(item) => Redirect(countryRoutes.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  /**
   * Fetch the existing set of repository ids. Remove the non-numeric (country code)
   * prefix, and increment to form a new id.
   */
  private def getNextRepositoryId(f: String => SimpleResult)(implicit userOpt: Option[UserProfile], request: RequestHeader): Future[SimpleResult] = {
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

          f(repoIdFormat.format(id))
        }
      }
    }
  }

  def createRepository(id: String) = childCreateAction.async(id, ContentTypes.Repository) {
      item => users => groups => implicit userOpt => implicit request =>

    // Beware! This is dubious because there could easily be contention
    // if two repositories get created at the same time.
    // Currently there is not way to notify the user that they should just
    // reset the form or increment the ID manually.
    getNextRepositoryId { newid =>
      val form = childForm.bind(Map("identifier" -> newid))
      Ok(views.html.repository.create(
        item, form, VisibilityForm.form, users, groups, countryRoutes.createRepositoryPost(id)))
    }
  }

  def createRepositoryPost(id: String) = childCreatePostAction.async(id, childForm, ContentTypes.Repository) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.repository.create(item,
          errorForm, accForm, users, groups, countryRoutes.createRepositoryPost(id)))
      }
      case Right(citem) => immediate(Redirect(controllers.archdesc.routes.Repositories.get(citem.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id)))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, countryRoutes.deletePost(id),
        countryRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(countryRoutes.search())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, countryRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(countryRoutes.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        countryRoutes.addItemPermissions(id), countryRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        countryRoutes.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        countryRoutes.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        countryRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(countryRoutes.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        countryRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(countryRoutes.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }
}

