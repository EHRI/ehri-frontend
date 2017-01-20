package controllers.keywords

import javax.inject._

import backend.rest.DataHelpers
import controllers.Components
import controllers.base.AdminController
import controllers.generic._
import defines.{EntityType, PermissionType}
import forms.VisibilityForm
import models._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import utils.{PageParams, RangeParams}
import utils.search._
import views.Helpers

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Concepts @Inject()(
  components: Components,
  dataHelpers: DataHelpers
) extends AdminController
  with Creator[ConceptF, Concept, Concept]
  with Visibility[Concept]
  with Read[Concept]
  with Update[ConceptF, Concept]
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
      request.item, form.fill(request.item.model), conceptRoutes.updatePost(id)))
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
      request.item, childForm, VisibilityForm.form,
      request.users, request.groups, conceptRoutes.createConceptPost(id)))
  }

  def createConceptPost(id: String): Action[AnyContent] = CreateChildAction(id, childForm).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm)) => dataHelpers.getUserAndGroupList.map { case (users, groups) =>
        BadRequest(views.html.admin.concept.create(request.item,
          errorForm, accForm, users, groups, conceptRoutes.createConceptPost(id)))
      }
      case Right(citem) => immediate(Redirect(conceptRoutes.get(id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def delete(id: String): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, conceptRoutes.deletePost(id), conceptRoutes.get(id)))
  }

  def deletePost(id: String): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(conceptRoutes.search())
      .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.users, request.groups, conceptRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String): Action[AnyContent] = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(conceptRoutes.get(id))
      .flashing("success" -> "item.update.confirmation")
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

  def linkTo(id: String): Action[AnyContent] = WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
    Ok(views.html.admin.concept.linkTo(request.item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value, params: SearchParams, paging: PageParams): Action[AnyContent] =
    LinkSelectAction(id, toType, params, paging).apply { implicit request =>
      Ok(views.html.admin.link.linkSourceList(
        request.item, request.searchResult, request.entityType,
        conceptRoutes.linkAnnotateSelect(id, toType),
        conceptRoutes.linkAnnotate))
    }
}


