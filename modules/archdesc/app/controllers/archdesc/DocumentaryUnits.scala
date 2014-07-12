package controllers.archdesc

import play.api.libs.concurrent.Execution.Implicits._
import forms.VisibilityForm
import models._
import controllers.generic._
import play.api.i18n.Messages
import defines.{ContentTypes,EntityType,PermissionType}
import views.Helpers
import utils.search._
import com.google.inject._
import solr.SolrConstants
import scala.concurrent.Future.{successful => immediate}
import backend.{ApiUser, Backend}
import utils.ListParams
import play.api.Play.current
import play.api.Configuration
import play.api.http.{HeaderNames, MimeTypes}


@Singleton
case class DocumentaryUnits @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends Read[DocumentaryUnit]
  with Visibility[DocumentaryUnit]
  with Creator[DocumentaryUnitF, DocumentaryUnit, DocumentaryUnit]
  with Update[DocumentaryUnitF, DocumentaryUnit]
  with Delete[DocumentaryUnit]
  with ScopePermissions[DocumentaryUnit]
  with Annotate[DocumentaryUnit]
  with Linking[DocumentaryUnit]
  with Descriptions[DocumentaryUnitDescriptionF, DocumentaryUnitF, DocumentaryUnit]
  with AccessPoints[DocumentaryUnitDescriptionF, DocumentaryUnitF, DocumentaryUnit]
  with Search
  with Api[DocumentaryUnit] {

  // Documentary unit facets
  import solr.facet._

  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key="childCount",
        name=Messages("documentaryUnit.searchInside"),
        param="items",
        render=s => Messages("documentaryUnit." + s),
        facets=List(
          SolrQueryFacet(value = "false", solrValue = "0", name = Some("noChildItems")),
          SolrQueryFacet(value = "true", solrValue = "[1 TO *]", name = Some("hasChildItems"))
        )
      ),
      FieldFacetClass(
        key=IsadG.LANG_CODE,
        name=Messages("documentaryUnit." + IsadG.LANG_CODE),
        param="lang",
        render=Helpers.languageCodeToName,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="holderName",
        name=Messages("documentaryUnit.heldBy"),
        param="holder",
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="countryCode",
        name=Messages("repository.countryCode"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="copyrightStatus",
        name=Messages("copyrightStatus.copyright"),
        param="copyright",
        render=s => Messages("copyrightStatus." + s)
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
        key="scope",
        name=Messages("scope.scope"),
        param="scope",
        render=s => Messages("scope." + s)
      )
    )
  }


  implicit val resource = DocumentaryUnit.Resource

  val formDefaults: Option[Configuration] = current.configuration.getConfig(EntityType.DocumentaryUnit)

  val contentType = ContentTypes.DocumentaryUnit
  val targetContentTypes = Seq(ContentTypes.DocumentaryUnit)

  val form = models.DocumentaryUnit.form
  val childForm = models.DocumentaryUnit.form
  val descriptionForm = models.DocumentaryUnitDescription.form

  private val docRoutes = controllers.archdesc.routes.DocumentaryUnits


  def search = userProfileAction.async { implicit userOpt => implicit request =>
    // What filters we gonna use? How about, only list stuff here that
    // has no parent items - UNLESS there's a query, in which case we're
    // going to peer INSIDE items... dodgy logic, maybe...
    
    val filters = if (request.getQueryString(SearchParams.QUERY).filterNot(_.trim.isEmpty).isEmpty)
      Map(SolrConstants.TOP_LEVEL -> true) else Map.empty[String,Any]

    find[DocumentaryUnit](
      filters = filters,
      entities=List(resource.entityType),
      facetBuilder = entityFacets
    ).map { result =>
      Ok(views.html.documentaryUnit.search(
        result.page, result.params, result.facets,
        docRoutes.search()))
    }
  }

  def searchChildren(id: String) = itemPermissionAction.async[DocumentaryUnit](contentType, id) {
      item => implicit userOpt => implicit request =>
    find[DocumentaryUnit](
      filters = Map(SolrConstants.PARENT_ID -> item.id),
      facetBuilder = entityFacets
    ).map { result =>
      Ok(views.html.documentaryUnit.search(
        result.page, result.params, result.facets,
        docRoutes.search()))
    }
  }

  /*def get(id: String) = getWithChildrenAction(id) {
      item => page => params => annotations => links => implicit userOpt => implicit request =>

    Ok(views.html.documentaryUnit.show(
      item, page.copy(items = page.items.map(DocumentaryUnit.apply)), params, annotations, links))
  }*/

  def get(id: String) = getAction.async(id) { item => annotations => links => implicit userOpt => implicit request =>
    find[DocumentaryUnit](
      filters = Map(SolrConstants.PARENT_ID -> item.id),
      entities = List(EntityType.DocumentaryUnit),
      facetBuilder = entityFacets
    ).map { result =>
      Ok(views.html.documentaryUnit.show(item, result.page, result.params, result.facets,
          docRoutes.get(id), annotations, links))
    }
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.list(page, params))
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.edit(
      item, form.fill(item.model),
      docRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.documentaryUnit.edit(
          olditem, errorForm, docRoutes.updatePost(id)))
      case Right(item) => Redirect(docRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createDoc(id: String) = childCreateAction(id, contentType) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.create(
      item, childForm, formDefaults, VisibilityForm.form.fill(item.accessors.map(_.id)),
      users, groups, docRoutes.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction.async(id, childForm, contentType) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.documentaryUnit.create(item,
          errorForm, formDefaults, accForm, users, groups,
          docRoutes.createDocPost(id)))
      }
      case Right(doc) => immediate(Redirect(docRoutes.get(doc.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def createDescription(id: String) = withItemPermission[DocumentaryUnit](id, PermissionType.Update, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.createDescription(item,
        descriptionForm, formDefaults, docRoutes.createDescriptionPost(id)))
  }

  def createDescriptionPost(id: String) = createDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, descriptionForm) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => {
        Ok(views.html.documentaryUnit.createDescription(item,
          errorForm, formDefaults, docRoutes.createDescriptionPost(id)))
      }
      case Right(updated) => Redirect(docRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def updateDescription(id: String, did: String) = withItemPermission[DocumentaryUnit](id, PermissionType.Update, contentType) {
      item => implicit userOpt => implicit request =>
    itemOr404(item.model.description(did)) { desc =>
      Ok(views.html.documentaryUnit.editDescription(item,
        descriptionForm.fill(desc),
        docRoutes.updateDescriptionPost(id, did)))
    }
  }

  def updateDescriptionPost(id: String, did: String) = updateDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, did, descriptionForm) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => {
        Ok(views.html.documentaryUnit.editDescription(item,
          errorForm, docRoutes.updateDescriptionPost(id, did)))
      }
      case Right(updated) => Redirect(docRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def deleteDescription(id: String, did: String) = deleteDescriptionAction(id, did) {
      item => description => implicit userOpt => implicit request =>
    Ok(views.html.deleteDescription(item, description,
        docRoutes.deleteDescriptionPost(id, did),
        docRoutes.get(id)))
  }

  def deleteDescriptionPost(id: String, did: String) = deleteDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, did) {
      ok => implicit userOpt => implicit request =>
    Redirect(docRoutes.get(id))
        .flashing("success" -> "item.delete.confirmation")
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, docRoutes.deletePost(id),
        docRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(docRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String) = visibilityAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, docRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(docRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        docRoutes.addItemPermissions(id),
        docRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        docRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        docRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        docRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(docRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        docRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(docRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def linkTo(id: String) = withItemPermission[DocumentaryUnit](id, PermissionType.Annotate, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.linkTo(item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value) = linkSelectAction(id, toType) {
    item => page => params => facets => etype => implicit userOpt => implicit request =>
      Ok(views.html.link.linkSourceList(item, page, params, facets, etype,
          docRoutes.linkAnnotateSelect(id, toType),
          docRoutes.linkAnnotate))
  }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String) = linkAction(id, toType, to) {
      target => source => implicit userOpt => implicit request =>
    Ok(views.html.link.create(target, source,
        Link.form, docRoutes.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String) = linkPostAction(id, toType, to) {
      formOrAnnotation => implicit userOpt => implicit request =>
    formOrAnnotation match {
      case Left((target,source,errorForm)) => {
        BadRequest(views.html.link.create(target, source,
          errorForm, docRoutes.linkAnnotatePost(id, toType, to)))
      }
      case Right(annotation) => {
        Redirect(docRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
      }
    }
  }

  def linkMultiAnnotate(id: String) = linkMultiAction(id) {
      target => implicit userOpt => implicit request =>
    Ok(views.html.link.linkMulti(target,
        Link.multiForm, docRoutes.linkMultiAnnotatePost(id)))
  }

  def linkMultiAnnotatePost(id: String) = linkPostMultiAction(id) {
      formOrAnnotations => implicit userOpt => implicit request =>
    formOrAnnotations match {
      case Left((target,errorForms)) => {
        BadRequest(views.html.link.linkMulti(target,
          errorForms, docRoutes.linkMultiAnnotatePost(id)))
      }
      case Right(annotations) => {
        Redirect(docRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
      }
    }
  }

  def manageAccessPoints(id: String, descriptionId: String) = manageAccessPointsAction(id, descriptionId) {
      item => desc => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.editAccessPoints(item, desc))
  }

  import play.api.libs.concurrent.Execution.Implicits._
  import utils.ead.DocTree

  def exportEad(id: String) = optionalUserAction.async { implicit userOpt => implicit request =>
    import scala.concurrent.Future
    implicit val apiUser = ApiUser(userOpt.map(_.id))

    val params = ListParams(limit = -1) // can't get around large limits yet...
    val eadId: String = docRoutes.exportEad(id).absoluteURL(globalConfig.https)

    def fetchTree(id: String): Future[DocTree] = {
      for {
        doc <- backend.get[DocumentaryUnit](id)
        children <- backend.listChildren[DocumentaryUnit,DocumentaryUnit](id, params)
        trees <- Future.sequence(children.map(c => {
          if (c.childCount.getOrElse(0) > 0) fetchTree(c.id)
          else Future.successful(DocTree(eadId, c, Seq.empty))
        }))
      } yield DocTree(eadId, doc, trees)
    }

    // Ugh, need to fetch the repository manually to
    // ensure we have detailed address info. Otherwise we'll
    // only get mandatory fields and relations.
    def fetchRepository(rid: Option[String]): Future[Option[Repository]] = {
      rid.map { id =>
        backend.get[Repository](id).map(r => Some(r))
      } getOrElse {
        Future.successful(Option.empty[Repository])
      }
    }

    for {
      doc <- backend.get[DocumentaryUnit](id)
      repository <- fetchRepository(doc.holder.map(_.id))
      tree <- fetchTree(id)
      treeWithRepo = tree.copy(item = tree.item.copy(holder = repository))
    } yield {
      Ok(views.export.ead.Helpers.tidyXml(views.xml.export.ead.ead(treeWithRepo).body))
        .as(MimeTypes.XML)
        //.withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=$id-ead.xml")
    }
  }
}


