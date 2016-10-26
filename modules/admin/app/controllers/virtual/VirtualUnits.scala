package controllers.virtual

import javax.inject._

import backend.rest.cypher.Cypher
import backend.rest.{DataHelpers, ItemNotFound}
import backend.{Entity, IdGenerator}
import controllers.Components
import controllers.base.{AdminController, SearchVC}
import controllers.generic._
import defines.{ContentTypes, EntityType, PermissionType}
import forms.VisibilityForm
import models._
import models.base.{AnyModel, Description}
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.Action
import utils.search._
import views.Helpers

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class VirtualUnits @Inject()(
  components: Components,
  dataHelpers: DataHelpers,
  idGenerator: IdGenerator,
  cypher: Cypher
) extends AdminController
  with Read[VirtualUnit]
  with Visibility[VirtualUnit]
  with Create[VirtualUnitF,VirtualUnit]
  with Creator[VirtualUnitF, VirtualUnit, VirtualUnit]
  with Update[VirtualUnitF, VirtualUnit]
  with Delete[VirtualUnit]
  with ScopePermissions[VirtualUnit]
  with Annotate[VirtualUnit]
  with Linking[VirtualUnit]
  with Descriptions[DocumentaryUnitDescriptionF, VirtualUnitF, VirtualUnit]
  with Search
  with SearchVC {

  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key="childCount",
        name=Messages("virtualUnit.searchInside"),
        param="data",
        render=s => Messages("documentaryUnit." + s),
        facets=List(
          QueryFacet(value = "false", range = Val("0"), name = Some("noChildItems")),
          QueryFacet(value = "true", range = Val("1") to End, name = Some("hasChildItems"))
        )
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("facet.lod"),
        param="lod",
        render=s => Messages("facet.lod." + s),
        facets=List(
          QueryFacet(value = "low", range = Val("0") to Val("500")),
          QueryFacet(value = "medium", range = Val("501") to Val("2000")),
          QueryFacet(value = "high", range = Val("2001") to End)
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key=Description.LANG_CODE,
        name=Messages("virtualUnit." + Description.LANG_CODE),
        param="lang",
        render=Helpers.languageCodeToName,
        display = FacetDisplay.Choice
      )
    )
  }

  val formDefaults: Option[Configuration] = config.getConfig(EntityType.VirtualUnit.toString)

  val targetContentTypes = Seq(ContentTypes.VirtualUnit)

  val form = models.VirtualUnit.form
  val childForm = models.VirtualUnit.form
  val descriptionForm = models.DocumentaryUnitDescription.form

  private def makeId(id: String) = s"vu-$id"

  private val vuRoutes = controllers.virtual.routes.VirtualUnits

  def contentsOf(id: String) = Action.async { implicit request =>
    vcDescendantIds(id).map { seq =>
      Ok(play.api.libs.json.Json.toJson(seq))
    }
  }

  def search = OptionalUserAction.async { implicit request =>
  // What filters we gonna use? How about, only list stuff here that
  // has no parent items - UNLESS there's a query, in which case we're
  // going to peer INSIDE items... dodgy logic, maybe...

    val filters = if (request.getQueryString(SearchParams.QUERY).forall(_.trim.isEmpty))
      Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String,Any]
    find[VirtualUnit](
      filters = filters,
      entities = List(EntityType.VirtualUnit),
      facetBuilder = entityFacets
    ).map { result =>
      Ok(views.html.admin.virtualUnit.search(result, vuRoutes.search()))
    }
  }

  def searchChildren(id: String) = ItemPermissionAction(id).async { implicit request =>
    for {
      ids <- descendantIds(request.item)
      result <- find[AnyModel](
        filters = buildChildSearchFilter(request.item),
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
        facetBuilder = entityFacets,
        idFilters = ids
      )
    } yield {
      Ok(views.html.admin.virtualUnit.search(result, vuRoutes.search()))
    }
  }

  def get(id: String) = ItemMetaAction(id).async { implicit request =>
    for {
      result <- find[AnyModel](
        filters = buildChildSearchFilter(request.item),
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
        facetBuilder = entityFacets)
    } yield {
      Ok(views.html.admin.virtualUnit.show(request.item, result,
        vuRoutes.get(id), request.annotations, request.links, Seq.empty))
    }
  }

  def getInVc(pathStr: String, id: String) = OptionalUserAction.async { implicit request =>
    val pathIds = pathStr.split(",").toSeq

    val pathF: Future[Seq[AnyModel]] = Future.sequence(pathIds.map(pid => userDataApi.getAny[AnyModel](pid)))
    val itemF: Future[AnyModel] = userDataApi.getAny[AnyModel](id)
    val linksF: Future[Seq[Link]] = userDataApi.links[Link](id)
    val annsF: Future[Seq[Annotation]] = userDataApi.annotations[Annotation](id)
    for {
      item <- itemF
      path <- pathF
      links <- linksF
      annotations <- annsF
      children <- find[AnyModel](
        filters = buildChildSearchFilter(item),
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
        facetBuilder = entityFacets)
    } yield Ok(views.html.admin.virtualUnit.showVc(
        item, children,
        vuRoutes.getInVc(id, pathStr), annotations, links, path))
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list = ItemPageAction.apply { implicit request =>
    Ok(views.html.admin.virtualUnit.list(request.page, request.params))
  }

  def update(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.edit(
      request.item, form.fill(request.item.model), vuRoutes.updatePost(id)))
  }

  def updatePost(id: String) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.virtualUnit.edit(
          request.item, errorForm, vuRoutes.updatePost(id)))
      case Right(item) => Redirect(vuRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def create = NewItemAction.async { implicit request =>
    idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit, "%06d").map { newId =>
      Ok(views.html.admin.virtualUnit.create(None, form.bind(Map(Entity.IDENTIFIER -> makeId(newId))),
        VisibilityForm.form,
        request.users, request.groups, vuRoutes.createPost()))
    }
  }

  def createPost = CreateItemAction(form).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => dataHelpers.getUserAndGroupList.map { case (users, groups) =>
        BadRequest(views.html.admin.virtualUnit.create(None, errorForm, accForm,
          users, groups, vuRoutes.createPost()))
      }
      case Right(item) => immediate(Redirect(vuRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def createChild(id: String) = NewChildAction(id).async { implicit request =>
    idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit, "%06d").map { newId =>
      Ok(views.html.admin.virtualUnit.create(
        Some(request.item), childForm.bind(Map(Entity.IDENTIFIER -> makeId(newId))),
        VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups, vuRoutes.createChildPost(id)))
    }
  }

  def createChildPost(id: String) = CreateChildAction(id, childForm).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => dataHelpers.getUserAndGroupList.map { case (users, groups) =>
        BadRequest(views.html.admin.virtualUnit.create(Some(request.item),
          errorForm, accForm, users, groups,
          vuRoutes.createChildPost(id)))
      }
      case Right(doc) => immediate(Redirect(vuRoutes.getInVc(id, doc.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  private val refForm = Form(single(VirtualUnitF.INCLUDE_REF -> nonEmptyText))

  def createChildRef(id: String) = EditAction(id).apply { implicit request =>
      Ok(views.html.admin.virtualUnit.createRef(
        request.item,
        refForm,
        vuRoutes.createChildRefPost(id)
      ))
  }

  def createChildRefPost(id: String) = EditAction(id).async { implicit request =>
    val boundForm = refForm.bindFromRequest()
    boundForm.fold(
      errForm => immediate(Ok(views.html.admin.virtualUnit.createRef(
        request.item,
        errForm,
        vuRoutes.createChildRefPost(id)
      ))),
      includes => userDataApi.addReferences[VirtualUnit](id, includes.split("[ ,]+").map(_.trim).toSeq).map { _ =>
        Redirect(vuRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
      } recover {
        case e: ItemNotFound =>
          val errs = boundForm.withError(VirtualUnitF.INCLUDE_REF, e.message.getOrElse(""))
          BadRequest(views.html.admin.virtualUnit.createRef(
            request.item,
            errs,
            vuRoutes.createChildRefPost(id)
          ))
      }
    )
  }

  def deleteChildRef(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.deleteRef(
      request.item,
      refForm,
      request.item.includedUnits.map(include => include.id -> s"${include.toStringLang} [${include.id}}]"),
      vuRoutes.deleteChildRefPost(id)
    ))
  }

  def deleteChildRefPost(id: String) = EditAction(id).async { implicit request =>
    val includes = request.item.includedUnits.map(include =>
      include.id -> include.toStringLang)
    val boundForm = refForm.bindFromRequest()

    boundForm.fold(
      errForm => immediate(Ok(views.html.admin.virtualUnit.deleteRef(
        request.item,
        errForm,
        includes,
        vuRoutes.deleteChildRefPost(id)
      ))),
      delete => userDataApi.deleteReferences[VirtualUnit](id, delete.split("[ ,]+").toSeq).map { _ =>
        Redirect(vuRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
      } recover {
        case e: ItemNotFound =>
          val errs = boundForm.withError(VirtualUnitF.INCLUDE_REF, e.message.getOrElse(""))
          BadRequest(views.html.admin.virtualUnit.deleteRef(
            request.item,
            errs,
            includes,
            vuRoutes.deleteChildRefPost(id)
          ))
      }
    )
  }

  def delete(id: String) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
        request.item, vuRoutes.deletePost(id),
        vuRoutes.get(id)))
  }

  def deletePost(id: String) = DeleteAction(id).apply { implicit request =>
    Redirect(vuRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def createDescription(id: String) = WithItemPermissionAction(id, PermissionType.Update).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.createDescription(request.item,
      descriptionForm, formDefaults, vuRoutes.createDescriptionPost(id)))
  }

  def createDescriptionPost(id: String) = CreateDescriptionAction(id, descriptionForm).apply { implicit request =>
    request.formOrDescription match {
      case Left(errorForm) =>
        Ok(views.html.admin.virtualUnit.createDescription(request.item,
          errorForm, formDefaults, vuRoutes.createDescriptionPost(id)))
      case Right(updated) => Redirect(vuRoutes.get(id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def updateDescription(id: String, did: String) = {
    WithDescriptionAction(id, did).apply { implicit request =>
      Ok(views.html.admin.virtualUnit.editDescription(request.item,
        descriptionForm.fill(request.description), vuRoutes.updateDescriptionPost(id, did)))
    }
  }

  def updateDescriptionPost(id: String, did: String) = {
    UpdateDescriptionAction(id, did, descriptionForm).apply { implicit request =>
      request.formOrDescription match {
        case Left(errorForm) =>
          Ok(views.html.admin.virtualUnit.editDescription(request.item,
            errorForm, vuRoutes.updateDescriptionPost(id, did)))
        case Right(updated) => Redirect(vuRoutes.get(id))
          .flashing("success" -> "item.create.confirmation")
      }
    }
  }

  def deleteDescription(id: String, did: String) = {
    WithDescriptionAction(id, did).apply { implicit request =>
      Ok(views.html.admin.deleteDescription(request.item, request.description,
        vuRoutes.deleteDescriptionPost(id, did), vuRoutes.get(id)))
    }
  }

  def deleteDescriptionPost(id: String, did: String) = {
    DeleteDescriptionAction(id, did).apply { implicit request =>
      Redirect(vuRoutes.get(id))
        .flashing("success" -> "item.delete.confirmation")
    }
  }

  def visibility(id: String) = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
        VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups, vuRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(vuRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = ScopePermissionGrantAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.manageScopedPermissions(
      request.item, request.permissionGrants, request.scopePermissionGrants,
        vuRoutes.addItemPermissions(id),
        vuRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.users, request.groups,
        vuRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.users, request.groups,
        vuRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        vuRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(vuRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        vuRoutes.setScopedPermissionsPost(id, userType, userId)))
    }
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(vuRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def linkTo(id: String) = WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.linkTo(request.item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value) = LinkSelectAction(id, toType).apply { implicit request =>
    Ok(views.html.admin.link.linkSourceList(
      request.item, request.searchResult, request.entityType,
        vuRoutes.linkAnnotateSelect(id, toType), vuRoutes.linkAnnotate))
  }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String) = LinkAction(id, toType, to).apply { implicit request =>
    Ok(views.html.admin.link.create(request.from, request.to,
        Link.form, vuRoutes.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String) = CreateLinkAction(id, toType, to).apply { implicit request =>
    request.formOrLink match {
      case Left((target,errorForm)) =>
        BadRequest(views.html.admin.link.create(request.from, target,
          errorForm, vuRoutes.linkAnnotatePost(id, toType, to)))
      case Right(_) =>
        Redirect(vuRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
    }
  }

  def linkMultiAnnotate(id: String) = WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
    Ok(views.html.admin.link.linkMulti(request.item,
        Link.multiForm, vuRoutes.linkMultiAnnotatePost(id)))
  }

  def linkMultiAnnotatePost(id: String) = CreateMultipleLinksAction(id).apply { implicit request =>
    request.formOrLinks match {
      case Left(errorForms) =>
        BadRequest(views.html.admin.link.linkMulti(request.item,
          errorForms, vuRoutes.linkMultiAnnotatePost(id)))
      case Right(_) =>
        Redirect(vuRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
    }
  }
}


