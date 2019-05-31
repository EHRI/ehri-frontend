package controllers.portal.annotate

import javax.inject._

import com.google.common.net.HttpHeaders
import controllers.AppComponents
import controllers.generic.{Promotion, Read, Search, Visibility}
import controllers.portal.FacetConfig
import controllers.portal.base.PortalController
import defines.{EntityType, PermissionType}
import models.view.AnnotationContext
import models.{Annotation, AnnotationF, UserProfile}
import play.api.libs.ws.WSClient
import play.api.mvc.{Result, _}
import services.data.DataHelpers
import services.search.SearchParams
import utils.PageParams

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Annotations @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  ws: WSClient,
  dataHelpers: DataHelpers,
  fc: FacetConfig
) extends PortalController
  with Read[Annotation]
  with Visibility[Annotation]
  with Promotion[Annotation]
  with Search {

  private val annotationRoutes = controllers.portal.annotate.routes.Annotations

  private val annotationDefaults: Map[String,String] = Map(
    AnnotationF.IS_PRIVATE -> true.toString
  )

  def searchAll(params: SearchParams, paging: PageParams): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    find[Annotation](params, paging, entities = List(EntityType.Annotation), facetBuilder = fc.annotationFacets).map { result =>
      Ok(views.html.annotation.list(result, annotationRoutes.searchAll()))
    }
  }

  def browse(id: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    userDataApi.get[Annotation](id).map { ann =>
      Ok(views.html.annotation.show(ann))
    }
  }

  // Ajax
  def annotate(id: String, did: String): Action[AnyContent] = WithUserAction {  implicit request =>
    Ok(views.html.annotation.create(
      Annotation.form.bind(annotationDefaults),
      annotationRoutes.annotatePost(id, did)
    )
    )
  }

  // Ajax
  def annotatePost(id: String, did: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    Annotation.form.bindFromRequest.fold(
      errorForm => immediate(BadRequest(errorForm.errorsAsJson)),
      ann => {
        val accessors: Seq[String] = getAccessors(ann, request.user)
        userDataApi.createAnnotation[Annotation,AnnotationF](id, ann, accessors, Some(did)).map { ann =>
          Created(views.html.annotation.annotationInline(ann, editable = true))
            .withHeaders(
                HttpHeaders.LOCATION -> annotationRoutes.browse(ann.id).url)
        }
      }
    )
  }

  // Ajax
  def editAnnotation(aid: String, context: AnnotationContext.Value): Action[AnyContent] = {
    WithItemPermissionAction(aid, PermissionType.Update).apply { implicit request =>
      Ok(views.html.annotation.edit(Annotation.form.fill(request.item.data),
        annotationRoutes.editAnnotationPost(aid, context)))
    }
  }

  def editAnnotationPost(aid: String, context: AnnotationContext.Value): Action[AnyContent] = {
    WithItemPermissionAction(aid, PermissionType.Update).async { implicit request =>
      // save an override field, becuase it's not possible to change it.
      val field = request.item.data.field
      Annotation.form.bindFromRequest.fold(
        errForm => immediate(BadRequest(errForm.errorsAsJson)),
        edited => userDataApi.update[Annotation,AnnotationF](aid, edited.copy(field = field)).flatMap { updated =>
          // Because the user might have marked this item
          // private (removing the isPromotable flag) we need to
          // recalculate who can access it.
          val newAccessors = getAccessors(updated.data, request.userOpt.get)
          if (newAccessors.sorted == updated.accessors.map(_.id).sorted)
            immediate(annotationResponse(updated, context))
          else userDataApi.setVisibility[Annotation](aid, newAccessors).map { ann =>
            annotationResponse(ann, context)
          }
        }
      )
    }
  }

  // Ajax
  def deleteAnnotation(aid: String): Action[AnyContent] = WithItemPermissionAction(aid, PermissionType.Delete).apply { implicit request =>
      Ok(views.html.helpers.simpleForm("annotation.delete.title",
          annotationRoutes.deleteAnnotationPost(aid)))
  }

  def deleteAnnotationPost(aid: String): Action[AnyContent] = WithItemPermissionAction(aid, PermissionType.Delete).async { implicit request =>
    userDataApi.delete[Annotation](aid).map { done =>
      Ok(true.toString)
    }
  }

  // Ajax
  def annotateField(id: String, did: String, field: String): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.annotation.create(
      Annotation.form.bind(annotationDefaults),
      annotationRoutes.annotateFieldPost(id, did, field)))
  }

  // Ajax
  def annotateFieldPost(id: String, did: String, field: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    Annotation.form.bindFromRequest.fold(
      errorForm => immediate(BadRequest(errorForm.errorsAsJson)),
      ann => {
        // Add the field to the model!
        val fieldAnn = ann.copy(field = Some(field))
        val accessors: Seq[String] = getAccessors(ann, request.user)
        userDataApi.createAnnotation[Annotation,AnnotationF](id, fieldAnn, accessors, Some(did)).map { ann =>
          Created(views.html.annotation.annotationInline(ann, editable = true))
            .withHeaders(
              HttpHeaders.LOCATION -> annotationRoutes.browse(ann.id).url)
        }
      }
    )
  }

  private def annotationResponse(item: Annotation, context: AnnotationContext.Value)(
      implicit userOpt: Option[UserProfile], request: RequestHeader): Result = {
    Ok {
      // if rendering with Ajax check which partial to return via the context param.
      if (isAjax) context match {
        case AnnotationContext.List => views.html.annotation.searchItem(item)
        case AnnotationContext.Field => views.html.annotation.annotationInline(item, editable = item.isOwnedBy(userOpt))
        case AnnotationContext.Block => views.html.annotation.annotationInline(item, editable = item.isOwnedBy(userOpt))
      } else views.html.annotation.show(item)
    }
  }

  def promoteAnnotation(id: String, context: AnnotationContext.Value): Action[AnyContent] = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.helpers.simpleForm("promotion.promote.title",
      annotationRoutes.promoteAnnotationPost(id, context)))
  }

  def promoteAnnotationPost(id: String, context: AnnotationContext.Value): Action[AnyContent] = PromoteItemAction(id).apply { implicit request =>
    annotationResponse(request.item, context)
  }

  def removeAnnotationPromotion(id: String, context: AnnotationContext.Value): Action[AnyContent] = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.helpers.simpleForm("promotion.promote.remove.title",
      annotationRoutes.removeAnnotationPromotionPost(id, context)))
  }

  def removeAnnotationPromotionPost(id: String, context: AnnotationContext.Value): Action[AnyContent] = RemovePromotionAction(id).apply { implicit request =>
    annotationResponse(request.item, context)
  }

  def demoteAnnotation(id: String, context: AnnotationContext.Value): Action[AnyContent] = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.helpers.simpleForm("promotion.demote.title",
      annotationRoutes.demoteAnnotationPost(id, context)))
  }

  def demoteAnnotationPost(id: String, context: AnnotationContext.Value): Action[AnyContent] = DemoteItemAction(id).apply { implicit request =>
    annotationResponse(request.item, context)
  }

  def removeAnnotationDemotion(id: String, context: AnnotationContext.Value): Action[AnyContent] = PromoteItemAction(id).apply { implicit request =>
    Ok(views.html.helpers.simpleForm("promotion.demote.remove.title",
      annotationRoutes.removeAnnotationDemotionPost(id, context)))
  }

  def removeAnnotationDemotionPost(id: String, context: AnnotationContext.Value): Action[AnyContent] = RemoveDemotionAction(id).apply { implicit request =>
    annotationResponse(request.item, context)
  }

  /**
   * Convert a contribution visibility value to the correct
   * accessors for the dataApi
   */
  private def getAccessors(ann: AnnotationF, user: UserProfile)(implicit request: Request[AnyContent]): Seq[String] =
    (if (ann.isPromotable) Seq(user.id) ++ getModerators else Seq(user.id)).distinct

  private def optionalConfigList(key: String): Seq[String] =
    config.getOptional[Seq[String]](key).getOrElse(Seq.empty)

  private def getModerators: Seq[String] = {
    val all = optionalConfigList("ehri.portal.moderators.all")
    val typed = optionalConfigList(s"ehri.portal.moderators.${EntityType.Annotation}")
    all ++ typed
  }
}
