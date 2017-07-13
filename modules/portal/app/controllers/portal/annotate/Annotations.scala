package controllers.portal.annotate

import javax.inject._

import services.rest.DataHelpers
import services.rest.cypher.Cypher
import com.google.common.net.HttpHeaders
import controllers.AppComponents
import controllers.generic.{Promotion, Read, Search, Visibility}
import controllers.portal.FacetConfig
import controllers.portal.base.PortalController
import defines.{EntityType, PermissionType}
import eu.ehri.project.definitions.Ontology
import forms.VisibilityForm
import models.view.AnnotationContext
import models.{Annotation, AnnotationF, UserProfile}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.{Result, _}
import utils.search.SearchParams
import utils.{ContributionVisibility, PageParams}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Annotations @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  ws: WSClient,
  dataHelpers: DataHelpers,
  cypher: Cypher,
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
      if (isAjax) Ok(Json.toJson(ann)(client.json.annotationJson.clientFormat))
      else Ok(views.html.annotation.show(ann))
    }
  }

  // Ajax
  def annotate(id: String, did: String): Action[AnyContent] = WithUserAction.async {  implicit request =>
    getCanShareWith(request.user) { users => groups =>
      Ok(views.html.annotation.create(
          Annotation.form.bind(annotationDefaults),
          ContributionVisibility.form.bindFromRequest,
          VisibilityForm.form.bindFromRequest,
          annotationRoutes.annotatePost(id, did),
          users, groups
        )
      )
    }
  }

  // Ajax
  def annotatePost(id: String, did: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    Annotation.form.bindFromRequest.fold(
      errorForm => immediate(BadRequest(errorForm.errorsAsJson)),
      ann => {
        val accessors: Seq[String] = getAccessors(ann, request.user)
        userDataApi.createAnnotation[Annotation,AnnotationF](id, ann, accessors, Some(did)).map { ann =>
          Created(views.html.annotation.annotationBlock(ann, editable = true))
            .withHeaders(
                HttpHeaders.LOCATION -> annotationRoutes.browse(ann.id).url)
        }
      }
    )
  }

  // Ajax
  def editAnnotation(aid: String, context: AnnotationContext.Value): Action[AnyContent] = {
    WithItemPermissionAction(aid, PermissionType.Update).async { implicit request =>
      val vis = getContributionVisibility(request.item, request.userOpt.get)
      getCanShareWith(request.userOpt.get) { users => groups =>
        Ok(views.html.annotation.edit(Annotation.form.fill(request.item.model),
          ContributionVisibility.form.fill(vis),
          VisibilityForm.form.fill(request.item.accessors.map(_.id)),
          annotationRoutes.editAnnotationPost(aid, context),
          users, groups))
      }
    }
  }

  def editAnnotationPost(aid: String, context: AnnotationContext.Value): Action[AnyContent] = {
    WithItemPermissionAction(aid, PermissionType.Update).async { implicit request =>
      // save an override field, becuase it's not possible to change it.
      val field = request.item.model.field
      Annotation.form.bindFromRequest.fold(
        errForm => immediate(BadRequest(errForm.errorsAsJson)),
        edited => userDataApi.update[Annotation,AnnotationF](aid, edited.copy(field = field)).flatMap { updated =>
          // Because the user might have marked this item
          // private (removing the isPromotable flag) we need to
          // recalculate who can access it.
          val newAccessors = getAccessors(updated.model, request.userOpt.get)
          if (newAccessors.sorted == updated.accessors.map(_.id).sorted)
            immediate(annotationResponse(updated, context))
          else userDataApi.setVisibility[Annotation](aid, newAccessors).map { ann =>
            annotationResponse(ann, context)
          }
        }
      )
    }
  }

  def setAnnotationVisibilityPost(aid: String): Action[AnyContent] = {
    WithItemPermissionAction(aid, PermissionType.Update).async { implicit request =>
      val accessors = getAccessors(request.item.model, request.userOpt.get)
      userDataApi.setVisibility[Annotation](aid, accessors).map { ann =>
        Ok(Json.toJson(ann.accessors.map(_.id)))
      }
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
  def annotateField(id: String, did: String, field: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    getCanShareWith(request.user) { users => groups =>
      Ok(views.html.annotation.create(
        Annotation.form.bind(annotationDefaults),
        ContributionVisibility.form.bindFromRequest,
        VisibilityForm.form.bindFromRequest,
        annotationRoutes.annotateFieldPost(id, did, field),
        users, groups
      )
      )
    }
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
        case AnnotationContext.Block => views.html.annotation.annotationBlock(item, editable = item.isOwnedBy(userOpt))
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
  private def getAccessors(ann: AnnotationF, user: UserProfile)(implicit request: Request[AnyContent]): Seq[String] = {
    val default: Seq[String] = utils.ContributionVisibility.form.bindFromRequest.fold(
      errForm => Seq(user.id), {
        case ContributionVisibility.Me => Seq(user.id)
        case ContributionVisibility.Groups => user.groups.map(_.id)
        case ContributionVisibility.Custom =>
          VisibilityForm.form.bindFromRequest.fold(
            err => List(user.id), // default to user visibility.
            list => list :+ user.id
          )
      }
    )
    val withMods = if (ann.isPromotable) default ++ getModerators else default
    withMods.distinct
  }

  private def optionalConfigList(key: String): Seq[String] =
    config.getOptional[Seq[String]](key).getOrElse(Seq.empty)

  private def getModerators: Seq[String] = {
    val all = optionalConfigList("ehri.portal.moderators.all")
    val typed = optionalConfigList(s"ehri.portal.moderators.${EntityType.Annotation}")
    all ++ typed
  }

  /**
   * Convert accessors to contribution visibility enum var...
   */
  private def getContributionVisibility(annotation: Annotation, user: UserProfile): ContributionVisibility.Value = {
    annotation.accessors.map(_.id).sorted.toList match {
      case id :: Nil if id == user.id => ContributionVisibility.Me
      case g if g.sorted == user.groups.map(_.id).sorted => ContributionVisibility.Groups
      case _ => ContributionVisibility.Custom
    }
  }

  /**
   * Get other users who belong to a user's groups.
   */
  private def getCanShareWith(user: UserProfile)(f: Seq[(String,String)] => Seq[(String,String)] => Result): Future[Result] = {

    import play.api.libs.json._

    val cypherQ =
      """
        |MATCH (n:UserProfile)-[:belongsTo*]->(g:Group)<-[:belongsTo]-(u:_Entity)
        |WHERE u.__id <> {user} AND n.__id = {user}
        |RETURN DISTINCT u.__id, u.name
      """.stripMargin

    cypher.cypher(cypherQ,
        Map("user" -> JsString(user.id),
          "label" -> JsString(Ontology.ACCESSOR_BELONGS_TO_GROUP))).map { json =>
        val users: Seq[(String,String)] = json.as[List[(String,String)]](
          (__ \ "data").read[List[List[String]]].map(all =>  all.map(l => (l.head, l(1))))
        )

      f(users)(user.groups.map(g => (g.id, g.model.name)))
    }
  }
}
