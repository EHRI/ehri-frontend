package controllers.virtual

import controllers.AppComponents
import controllers.base.{AdminController, SearchVC}
import controllers.generic._
import forms._

import javax.inject._
import models._
import forms.ConfigFormFieldHintsBuilder
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.cypher.CypherService
import services.data.{DataHelpers, IdGenerator, ItemNotFound}
import services.search._
import utils.{PageParams, RangeParams}
import views.Helpers

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class VirtualUnits @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  idGenerator: IdGenerator,
  cypher: CypherService
) extends AdminController
  with Read[VirtualUnit]
  with Visibility[VirtualUnit]
  with Create[VirtualUnit]
  with Creator[VirtualUnit, VirtualUnit]
  with Update[VirtualUnit]
  with Delete[VirtualUnit]
  with ScopePermissions[VirtualUnit]
  with Annotate[VirtualUnit]
  with Linking[VirtualUnit]
  with Descriptions[VirtualUnit]
  with Search
  with SearchVC {

  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key = "childCount",
        name = Messages("virtualUnit.searchInside"),
        param = "data",
        render = s => Messages("documentaryUnit." + s),
        facets = List(
          QueryFacet(value = "false", range = Val("0"), name = Some("noChildItems")),
          QueryFacet(value = "true", range = Val("1") to End, name = Some("hasChildItems"))
        )
      ),
      QueryFacetClass(
        key = "charCount",
        name = Messages("facet.lod"),
        param = "lod",
        render = s => Messages("facet.lod." + s),
        facets = List(
          QueryFacet(value = "low", range = Val("0") to Val("500")),
          QueryFacet(value = "medium", range = Val("501") to Val("2000")),
          QueryFacet(value = "high", range = Val("2001") to End)
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key = Description.LANG_CODE,
        name = Messages("virtualUnit." + Description.LANG_CODE),
        param = "lang",
        render = Helpers.languageCodeToName,
        display = FacetDisplay.Choice
      )
    )
  }

  override protected val targetContentTypes = Seq(ContentTypes.VirtualUnit)
  private val formConfig: ConfigFormFieldHintsBuilder = ConfigFormFieldHintsBuilder(EntityType.DocumentaryUnit, config)
  private val form: Form[VirtualUnitF] = models.VirtualUnit.form
  private val childForm: Form[VirtualUnitF] = models.VirtualUnit.form

  private def makeId(id: String) = s"vu-$id"

  private val vuRoutes = controllers.virtual.routes.VirtualUnits

  def contentsOf(id: String): Action[AnyContent] = Action.async { implicit request =>
    vcDescendantIds(id).map { seq =>
      Ok(play.api.libs.json.Json.toJson(seq))
    }
  }

  def search(params: SearchParams, paging: PageParams): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    // What filters we gonna use? How about, only list stuff here that
    // has no parent items - UNLESS there's a query, in which case we're
    // going to peer INSIDE items... dodgy logic, maybe...
    val filters = if (request.getQueryString(SearchParams.QUERY).forall(_.trim.isEmpty))
      Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String, Any]

    findType[VirtualUnit](params, paging, filters = filters, facetBuilder = entityFacets).map { result =>
      Ok(views.html.admin.virtualUnit.search(result, vuRoutes.search()))
    }
  }

  def get(id: String, dlid: Option[String], params: SearchParams, paging: PageParams): Action[AnyContent] = ItemMetaAction(id).async { implicit request =>
    for {
      filters <- vcSearchFilters(request.item)
      result <- find[Model](params, paging, filters = filters,
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit), facetBuilder = entityFacets)
    } yield {
      if (isAjax) Ok(views.html.admin.search.inlineItemList(result))
        .withHeaders("more" -> result.page.hasMore.toString)
      else Ok(views.html.admin.virtualUnit.show(request.item, result,
        vuRoutes.get(id), request.annotations, request.links, dlid, Seq.empty))
    }
  }

  def getInVc(pathStr: String, id: String, dlid: Option[String], params: SearchParams, paging: PageParams): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    val pathIds = pathStr.split(",").toSeq

    val pathF: Future[Seq[Model]] = Future.sequence(pathIds.map(pid => userDataApi.getAny[Model](pid)))
    val itemF: Future[Model] = userDataApi.getAny[Model](id)
    val linksF: Future[Seq[Link]] = userDataApi.links[Link](id)
    val annsF: Future[Seq[Annotation]] = userDataApi.annotations[Annotation](id)
    for {
      item <- itemF
      path <- pathF
      links <- linksF
      annotations <- annsF
      filters <- vcSearchFilters(item)
      children <- find[Model](params, paging, filters = filters,
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit), facetBuilder = entityFacets)
    } yield Ok(views.html.admin.virtualUnit.showVc(
      item, children,
      vuRoutes.getInVc(id, pathStr), annotations, links, dlid, path))
  }

  def history(id: String, range: RangeParams): Action[AnyContent] = ItemHistoryAction(id, range).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.edit(
      request.item, form.fill(request.item.data),formConfig.forUpdate, vuRoutes.updatePost(id)))
  }

  def updatePost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.virtualUnit.edit(
        request.item, errorForm, formConfig.forUpdate, vuRoutes.updatePost(id)))
      case Right(item) => Redirect(vuRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def create: Action[AnyContent] = NewItemAction.async { implicit request =>
    idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit, "%06d").map { newId =>
      Ok(views.html.admin.virtualUnit.create(None, form.bind(Map(Entity.IDENTIFIER -> makeId(newId))),
        formConfig.forCreate, visibilityForm, request.usersAndGroups, vuRoutes.createPost()))
    }
  }

  def createPost: Action[AnyContent] = CreateItemAction(form).apply { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm, usersAndGroups)) =>
        BadRequest(views.html.admin.virtualUnit.create(None, errorForm, formConfig.forCreate, accForm,
          usersAndGroups, vuRoutes.createPost()))
      case Right(item) => Redirect(vuRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def createChild(id: String): Action[AnyContent] = NewChildAction(id).async { implicit request =>
    idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit, "%06d").map { newId =>
      Ok(views.html.admin.virtualUnit.create(
        Some(request.item), childForm.bind(Map(Entity.IDENTIFIER -> makeId(newId))),
        formConfig.forCreate, visibilityForm.fill(request.item.accessors.map(_.id)),
        request.usersAndGroups, vuRoutes.createChildPost(id)))
    }
  }

  def createChildPost(id: String): Action[AnyContent] = CreateChildAction(id, childForm).apply { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm, usersAndGroups)) =>
        BadRequest(views.html.admin.virtualUnit.create(Some(request.item),
          errorForm, formConfig.forCreate, accForm, usersAndGroups,
          vuRoutes.createChildPost(id)))
      case Right(doc) => Redirect(vuRoutes.getInVc(id, doc.id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  private val refForm = Form(single(VirtualUnitF.INCLUDE_REF -> nonEmptyText))

  def createChildRef(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.createRef(
      request.item,
      refForm,
      vuRoutes.createChildRefPost(id)
    ))
  }

  def createChildRefPost(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
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

  def deleteChildRef(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.deleteRef(
      request.item,
      refForm,
      request.item.includedUnits.map(include => include.id -> s"${include.toStringLang} [${include.id}}]"),
      vuRoutes.deleteChildRefPost(id)
    ))
  }

  def deleteChildRefPost(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
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

  def delete(id: String): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, vuRoutes.deletePost(id),
      vuRoutes.get(id)))
  }

  def deletePost(id: String): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(vuRoutes.search())
      .flashing("success" -> "item.delete.confirmation")
  }

  def createDescription(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.createDescription(request.item,
      form.fill(request.item.data), formConfig.forCreate, vuRoutes.createDescriptionPost(id)))
  }

  def createDescriptionPost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        Ok(views.html.admin.virtualUnit.createDescription(request.item,
          errorForm, formConfig.forCreate, vuRoutes.createDescriptionPost(id)))
      case Right(updated) => Redirect(vuRoutes.get(id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def updateDescription(id: String, did: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.editDescription(request.item,
      form.fill(request.item.data), formConfig.forUpdate, did, vuRoutes.updateDescriptionPost(id, did)))
  }

  def updateDescriptionPost(id: String, did: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        Ok(views.html.admin.virtualUnit.editDescription(request.item,
          errorForm, formConfig.forUpdate, did, vuRoutes.updateDescriptionPost(id, did)))
      case Right(updated) => Redirect(vuRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def deleteDescription(id: String, did: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.deleteDescription(request.item, form.fill(request.item.data), did,
      vuRoutes.deleteDescriptionPost(id, did)))
  }

  def deleteDescriptionPost(id: String, did: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        Ok(views.html.admin.virtualUnit.deleteDescription(request.item,
          errorForm, did, vuRoutes.deleteDescriptionPost(id, did)))
      case Right(updated) => Redirect(vuRoutes.get(id))
        .flashing("success" -> "item.delete.confirmation")
    }
  }

  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      visibilityForm.fill(request.item.accessors.map(_.id)),
      request.usersAndGroups, vuRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String): Action[AnyContent] = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(vuRoutes.get(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String, paging: PageParams, scopePaging: PageParams): Action[AnyContent] =
    ScopePermissionGrantAction(id, paging, scopePaging).apply { implicit request =>
      Ok(views.html.admin.permissions.manageScopedPermissions(
        request.item, request.permissionGrants, request.scopePermissionGrants,
        vuRoutes.addItemPermissions(id),
        vuRoutes.addScopedPermissions(id)))
    }

  def addItemPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.usersAndGroups,
      vuRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.usersAndGroups,
      vuRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        vuRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(vuRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        vuRoutes.setScopedPermissionsPost(id, userType, userId)))
    }
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(vuRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def linkTo(id: String): Action[AnyContent] = WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.linkTo(request.item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value, params: SearchParams, paging: PageParams): Action[AnyContent] =
    LinkSelectAction(id, toType, params, paging).apply { implicit request =>
      Ok(views.html.admin.link.linkSourceList(
        request.item, request.searchResult, request.entityType,
        vuRoutes.linkAnnotateSelect(id, toType),
        (other, _) => vuRoutes.linkAnnotate(id, toType, other)))
    }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String): Action[AnyContent] =
    LinkAction(id, toType, to).apply { implicit request =>
      Ok(views.html.admin.link.create(request.from, request.to,
        Link.form, vuRoutes.linkAnnotatePost(id, toType, to)))
    }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String): Action[AnyContent] =
    CreateLinkAction(id, toType, to).apply { implicit request =>
      request.formOrLink match {
        case Left((target, errorForm)) =>
          BadRequest(views.html.admin.link.create(request.from, target,
            errorForm, vuRoutes.linkAnnotatePost(id, toType, to)))
        case Right(_) =>
          Redirect(vuRoutes.get(id))
            .flashing("success" -> "item.update.confirmation")
      }
    }
}


