package controllers.keywords

import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.{EntityType, PermissionType}
import forms._
import javax.inject._
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.data.DataHelpers
import services.search._
import utils.{PageParams, RangeParams}
import views.Helpers

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Concepts @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers
) extends AdminController
  with Creator[Concept, Concept]
  with Visibility[Concept]
  with Read[Concept]
  with Update[Concept]
  with Delete[Concept]
  with Linking[Concept]
  with Annotate[Concept]
  with SearchType[Concept] {

  private val form = models.Concept.form
  private val childForm = models.Concept.form
  private val conceptRoutes = controllers.keywords.routes.Concepts

  private def entityFacets: FacetBuilder = { implicit request =>
    import SearchConstants._
    List(
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("cvocConcept." + LANGUAGE_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.DropDown,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = HOLDER_NAME,
        name = Messages("cvocConcept.inVocabulary"),
        param = "set",
        sort = FacetSort.Name
      )
    )
  }


  def get(id: String, paging: PageParams): Action[AnyContent] = ItemMetaAction(id).async { implicit request =>
    userDataApi.children[Concept, Concept](id, paging).map { page =>
      Ok(views.html.admin.concept.show(request.item, page, paging, request.links, request.annotations))
    }
  }

  def search(params: SearchParams, paging: PageParams): Action[AnyContent] =
    SearchTypeAction(params, paging, facetBuilder = entityFacets).apply { implicit request =>
      Ok(views.html.admin.concept.search(request.result, conceptRoutes.search()))
    }

  def history(id: String, range: RangeParams): Action[AnyContent] = ItemHistoryAction(id, range).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list(paging: PageParams): Action[AnyContent] = ItemPageAction(paging).apply { implicit request =>
    Ok(views.html.admin.concept.list(request.page, request.params))
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.concept.edit(
      request.item, form.fill(request.item.data), conceptRoutes.updatePost(id)))
  }

  def updatePost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.concept.edit(
        request.item, errorForm, conceptRoutes.updatePost(id)))
      case Right(item) => Redirect(conceptRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createConcept(id: String): Action[AnyContent] = NewChildAction(id).apply { implicit request =>
    Ok(views.html.admin.concept.create(
      request.item, childForm, visibilityForm,
      request.usersAndGroups, conceptRoutes.createConceptPost(id)))
  }

  def createConceptPost(id: String): Action[AnyContent] = CreateChildAction(id, childForm).apply { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm, usersAndGroups)) =>
        BadRequest(views.html.admin.concept.create(request.item,
          errorForm, accForm, usersAndGroups, conceptRoutes.createConceptPost(id)))
      case Right(citem) => Redirect(conceptRoutes.get(id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def delete(id: String): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, conceptRoutes.deletePost(id), conceptRoutes.get(id),
      breadcrumbs = views.html.admin.concept.breadcrumbs(request.item)))
  }

  def deletePost(id: String): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(conceptRoutes.search())
      .flashing("success" -> "item.delete.confirmation")
  }

  private val broaderForm: Form[Seq[String]] = Form(
    single("broaderTerms" -> seq(nonEmptyText))
  )

  def setBroader(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.concept.broader(request.item,
      broaderForm.fill(request.item.broaderTerms.map(_.id)),
      conceptRoutes.setBroaderPost(id)))
  }

  def setBroaderPost(id: String): Action[AnyContent] =
    WithItemPermissionAction(id, PermissionType.Update).async { implicit request =>
      broaderForm.bindFromRequest().fold(
        err => immediate(BadRequest(views.html.admin.concept.broader(request.item, err,
            conceptRoutes.setBroaderPost(id)))),
        ids => userDataApi.parent[Concept, Concept](id, ids).map { item =>
         Redirect(conceptRoutes.get(id)).flashing("success" -> "item.update.confirmation")
        }
      )
    }

  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      visibilityForm.fill(request.item.accessors.map(_.id)),
      request.usersAndGroups, conceptRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String): Action[AnyContent] = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(conceptRoutes.get(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def linkTo(id: String): Action[AnyContent] = WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
    Ok(views.html.admin.concept.linkTo(request.item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value, params: SearchParams, paging: PageParams): Action[AnyContent] =
    LinkSelectAction(id, toType, params, paging).apply { implicit request =>
      Ok(views.html.admin.link.linkSourceList(
        request.item, request.searchResult, request.entityType,
        conceptRoutes.linkAnnotateSelect(id, toType),
        (other, _) => conceptRoutes.linkAnnotate(id, toType, other)))
    }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String): Action[AnyContent] =
    LinkAction(id, toType, to).apply { implicit request =>
      Ok(views.html.admin.link.create(request.from, request.to,
        Link.form, conceptRoutes.linkAnnotatePost(id, toType, to)))
    }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String): Action[AnyContent] =
    CreateLinkAction(id, toType, to).apply { implicit request =>
      request.formOrLink match {
        case Left((target, errorForm)) =>
          BadRequest(views.html.admin.link.create(request.from, target,
            errorForm, conceptRoutes.linkAnnotatePost(id, toType, to)))
        case Right(_) =>
          Redirect(conceptRoutes.get(id))
            .flashing("success" -> "item.update.confirmation")
      }
    }
}


