package controllers.archdesc

import play.api.libs.concurrent.Execution.Implicits._
import forms.VisibilityForm
import controllers.generic._
import models._
import play.api.i18n.Messages
import defines.{EntityType,ContentTypes}
import views.Helpers
import utils.search._
import com.google.inject._
import solr.SolrConstants
import scala.concurrent.Future.{successful => immediate}
import backend.{ApiUser, Backend}
import utils.ListParams
import scala.concurrent.ExecutionContext
import play.api.Configuration
import play.api.Play.current

@Singleton
case class Repositories @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchIndexer: Indexer, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends Read[Repository]
  with Update[RepositoryF, Repository]
  with Delete[Repository]
  with Creator[DocumentaryUnitF,DocumentaryUnit, Repository]
	with Visibility[Repository]
  with ScopePermissions[Repository]
  with Annotate[Repository]
  with Search
  with Api[Repository]
  with Indexable[Repository] {

  // Documentary unit facets
  import solr.facet._

  private val repositoryFacets: FacetBuilder = { implicit request =>
    val prefix = EntityType.Repository.toString
    List(
      QueryFacetClass(
        key="childCount",
        name=Messages(prefix + ".itemsHeldOnline"),
        param="items",
        render=s => Messages(prefix + "." + s),
        facets=List(
          SolrQueryFacet(value = "false", solrValue = "0", name = Some("noChildItems")),
          SolrQueryFacet(value = "true", solrValue = "[1 TO *]", name = Some("hasChildItems"))
        )
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("lod"),
        param="lod",
        render=s => Messages("lod." + s),
        facets=List(
          SolrQueryFacet(value = "low", solrValue = "[0 TO 500]", name = Some("low")),
          SolrQueryFacet(value = "medium", solrValue = "[501 TO 2000]", name = Some("medium")),
          SolrQueryFacet(value = "high", solrValue = "[2001 TO *]", name = Some("high"))
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key="countryCode",
        name=Messages(prefix + ".countryCode"),
        param="country",
        render=Helpers.countryCodeToName,
        display = FacetDisplay.DropDown,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key="priority",
        name=Messages("priority"),
        param="priority",
        sort = FacetSort.Name,
        display = FacetDisplay.Choice,
        render= {
          case s if s == "0" => Messages("priority.zero")
          case s if s == "1" => Messages("priority.one")
          case s if s == "2" => Messages("priority.two")
          case s if s == "3" => Messages("priority.three")
          case s if s == "4" => Messages("priority.four")
          case s if s == "5" => Messages("priority.five")
          case s if s == "-1" => Messages("priority.reject")
          case _ => Messages("priority.unknown")
        }
      )
    )
  }

  implicit val resource = Repository.Resource

  val contentType = ContentTypes.Repository
  val targetContentTypes = Seq(ContentTypes.DocumentaryUnit)

  private val form = models.Repository.form

  private val childFormDefaults: Option[Configuration] = current.configuration.getConfig(EntityType.DocumentaryUnit)

  private val childForm = models.DocumentaryUnit.form

  private val repositoryRoutes = controllers.archdesc.routes.Repositories


  def search = searchAction[Repository](entities = List(resource.entityType), entityFacets = repositoryFacets) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(views.html.repository.search(page, params, facets, repositoryRoutes.search()))
  }

  /**
   * Search documents inside repository.
   */
  def get(id: String) = getAction.async(id) { item => annotations => links => implicit userOpt => implicit request =>

    val filters = (if (request.getQueryString(SearchParams.QUERY).filterNot(_.trim.isEmpty).isEmpty)
      Map(SolrConstants.TOP_LEVEL -> true) else Map.empty[String,Any]) ++ Map(SolrConstants.HOLDER_ID -> item.id)

    find[DocumentaryUnit](
      filters = filters,
      entities = List(EntityType.DocumentaryUnit),
      facetBuilder = repositoryFacets
    ).map { result =>
      Ok(views.html.repository.show(item, result.page, result.params, result.facets,
        repositoryRoutes.get(id), annotations, links))
    }
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.repository.list(page, params))
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.repository.edit(item, form.fill(item.model), repositoryRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.repository.edit(item, errorForm, repositoryRoutes.updatePost(id)))
      case Right(doc) => Redirect(repositoryRoutes.get(doc.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createDoc(id: String) = childCreateAction(id, ContentTypes.DocumentaryUnit) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.create(item, childForm, childFormDefaults,
      VisibilityForm.form.fill(item.accessors.map(_.id)),
      users, groups, repositoryRoutes.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction.async(id, childForm, ContentTypes.DocumentaryUnit) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.documentaryUnit.create(item,
          errorForm, childFormDefaults, accForm, users, groups, repositoryRoutes.createDocPost(id)))
      }
      case Right(citem) => immediate(Redirect(controllers.archdesc.routes.DocumentaryUnits.get(citem.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(item, repositoryRoutes.deletePost(id),
        repositoryRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(repositoryRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
      VisibilityForm.form.fill(item.accessors.map(_.id)),
      users, groups, repositoryRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(repositoryRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        repositoryRoutes.addItemPermissions(id), repositoryRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        repositoryRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        repositoryRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        repositoryRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(repositoryRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        repositoryRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(repositoryRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def updateIndex(id: String) = adminAction.async { implicit userOpt => implicit request =>
    getEntity(id, userOpt) { item =>
      Ok(views.html.search.updateItemIndex(item,
        action = controllers.archdesc.routes.Repositories.updateIndexPost(id)))
    }
  }

  def updateIndexPost(id: String) = updateChildItemsPost(SolrConstants.HOLDER_ID, id)

  import play.api.libs.concurrent.Execution.Implicits._
  import utils.ead.DocTree

  def exportEad(id: String) = optionalUserAction.async { implicit userOpt => implicit request =>

    import scala.concurrent.Future
    implicit val apiUser = ApiUser(userOpt.map(_.id))

    val params = ListParams(limit = -1) // can't get around large limits yet...

    def fetchTree(doc: DocumentaryUnit): Future[DocTree] = {
      for {
        children <- backend.listChildren[DocumentaryUnit,DocumentaryUnit](doc.id, params)
        trees <- Future.sequence(children.map(c => {
          if (c.childCount.getOrElse(0) > 0) fetchTree(c)
          else Future.successful(DocTree(c, Seq.empty))
        }))
      } yield DocTree(doc, trees)
    }

    for {
      repo <- backend.get[Repository](id)
      docs <- backend.listChildren[Repository,DocumentaryUnit](id, params)
      trees <- Future.sequence(docs.map(c => {
        if (c.childCount.getOrElse(0) > 0) fetchTree(c)
        else Future.successful(DocTree(c, Seq.empty))
      }))
    } yield {
      Ok(views.export.ead.Helpers.tidyXml(
          views.xml.export.ead.repositoryEad(repo, trees).body)).as("text/xml")
    }
  }
}
