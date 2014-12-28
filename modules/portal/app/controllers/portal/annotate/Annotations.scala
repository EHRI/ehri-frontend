package controllers.portal.annotate

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import controllers.base.SessionPreferences
import controllers.generic.{Promotion, Visibility}
import models.{AnnotationF, Annotation, UserProfile}
import play.api.Play.current
import views.html.p
import utils.{SessionPrefs, ContributionVisibility}
import scala.concurrent.Future.{successful => immediate}
import defines.{EntityType, PermissionType}
import backend.rest.cypher.CypherDAO
import play.api.libs.json.Json
import eu.ehri.project.definitions.Ontology
import scala.concurrent.Future
import play.api.mvc.Result
import forms.VisibilityForm
import com.google.common.net.HttpHeaders
import backend.Backend
import utils.search.{Resolver, Dispatcher}
import models.view.AnnotationContext


import com.google.inject._
import controllers.portal.Secured
import controllers.portal.base.PortalController

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class Annotations @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: models.AccountDAO)
  extends PortalController
  with Visibility[Annotation]
  with Promotion[Annotation]
  with SessionPreferences[SessionPrefs]
  with Secured {

  val defaultPreferences = new SessionPrefs

  private val annotationRoutes = controllers.portal.annotate.routes.Annotations

  private val annotationDefaults: Map[String,String] = Map(
    AnnotationF.IS_PRIVATE -> true.toString
  )

  def annotation(id: String) = OptionalProfileAction.async { implicit request =>
    backend.get[Annotation](id).map { ann =>
      Ok(Json.toJson(ann)(client.json.annotationJson.clientFormat))
    }
  }

  // Ajax
  def annotate(id: String, did: String) = WithUserAction.async {  implicit request =>
    getCanShareWith(request.profile) { users => groups =>
      Ok(
        p.annotation.create(
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
  def annotatePost(id: String, did: String) = WithUserAction.async { implicit request =>
    Annotation.form.bindFromRequest.fold(
      errorForm => immediate(BadRequest(errorForm.errorsAsJson)),
      ann => {
        val accessors: List[String] = getAccessors(ann, request.profile)
        backend.createAnnotationForDependent[Annotation,AnnotationF](id, did, ann, accessors).map { ann =>
          Created(p.annotation.annotationBlock(ann, editable = true))
            .withHeaders(
                HttpHeaders.LOCATION -> annotationRoutes.annotation(ann.id).url)
        }
      }
    )
  }

  // Ajax
  def editAnnotation(aid: String, context: AnnotationContext.Value) = withItemPermission.async[Annotation](aid, PermissionType.Update) {
      item => implicit userOpt => implicit request =>
    val vis = getContributionVisibility(item, userOpt.get)
    getCanShareWith(userOpt.get) { users => groups =>
      Ok(p.annotation.edit(Annotation.form.fill(item.model),
        ContributionVisibility.form.fill(vis),
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        annotationRoutes.editAnnotationPost(aid, context),
        users, groups))
    }
  }

  def editAnnotationPost(aid: String, context: AnnotationContext.Value) = withItemPermission.async[Annotation](aid, PermissionType.Update) {
      item => implicit userOpt => implicit request =>
    // save an override field, becuase it's not possible to change it.
    val field = item.model.field
    Annotation.form.bindFromRequest.fold(
      errForm => immediate(BadRequest(errForm.errorsAsJson)),
      edited => backend.update[Annotation,AnnotationF](aid, edited.copy(field = field)).flatMap { updated =>
        // Because the user might have marked this item
        // private (removing the isPromotable flag) we need to
        // recalculate who can access it.
        val newAccessors = getAccessors(updated.model, userOpt.get)
        if (newAccessors.sorted == updated.accessors.map(_.id).sorted)
          immediate(annotationResponse(updated, context))
        else backend.setVisibility[Annotation](aid, newAccessors).map { ann =>
          annotationResponse(ann, context)
        }
      }
    )
  }

  def setAnnotationVisibilityPost(aid: String) = withItemPermission.async[Annotation](aid, PermissionType.Update) {
      item => implicit userOpt => implicit request =>
    val accessors = getAccessors(item.model, userOpt.get)
    backend.setVisibility[Annotation](aid, accessors).map { ann =>
      Ok(Json.toJson(ann.accessors.map(_.id)))
    }
  }

  // Ajax
  def deleteAnnotation(aid: String) = withItemPermission[Annotation](aid, PermissionType.Delete) {
        item => implicit userOpt => implicit request =>
      Ok(p.helpers.simpleForm("portal.annotation.delete.title",
          annotationRoutes.deleteAnnotationPost(aid)))
  }

  def deleteAnnotationPost(aid: String) = withItemPermission.async[Annotation](aid, PermissionType.Delete) {
      item => implicit userOpt => implicit request =>
    backend.delete[Annotation](aid).map { done =>
      Ok(true.toString)
    }
  }

  // Ajax
  def annotateField(id: String, did: String, field: String) = WithUserAction.async { implicit request =>
    getCanShareWith(request.profile) { users => groups =>
      Ok(p.annotation.create(
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
  def annotateFieldPost(id: String, did: String, field: String) = WithUserAction.async { implicit request =>
    Annotation.form.bindFromRequest.fold(
      errorForm => immediate(BadRequest(errorForm.errorsAsJson)),
      ann => {
        // Add the field to the model!
        val fieldAnn = ann.copy(field = Some(field))
        val accessors: List[String] = getAccessors(ann, request.profile)
        backend.createAnnotationForDependent[Annotation,AnnotationF](id, did, fieldAnn, accessors).map { ann =>
          Created(p.annotation.annotationInline(ann, editable = true))
            .withHeaders(
              HttpHeaders.LOCATION -> annotationRoutes.annotation(ann.id).url)
        }
      }
    )
  }

  private def annotationResponse(item: Annotation, context: AnnotationContext.Value)(
      implicit userOpt: Option[UserProfile], request: RequestHeader): Result = {
    Ok {
      // if rendering with Ajax check which partial to return via the context param.
      if (isAjax) context match {
        case AnnotationContext.List => p.annotation.searchItem(item)
        case AnnotationContext.Field => p.annotation.annotationInline(item, editable = item.isOwnedBy(userOpt))
        case AnnotationContext.Block => p.annotation.annotationBlock(item, editable = item.isOwnedBy(userOpt))
      } else p.annotation.show(item)
    }
  }

  def promoteAnnotation(id: String, context: AnnotationContext.Value) = promoteAction(id) {
      _ => implicit userOpt => implicit request =>
    Ok(p.helpers.simpleForm("portal.promotion.promote.title",
      annotationRoutes.promoteAnnotationPost(id, context)))
  }

  def promoteAnnotationPost(id: String, context: AnnotationContext.Value) = promotePostAction(id) {
      updated => implicit userOpt => implicit request =>
    annotationResponse(updated, context)
  }

  def removeAnnotationPromotion(id: String, context: AnnotationContext.Value) = promoteAction(id) {
      _ => implicit userOpt => implicit request =>
    Ok(p.helpers.simpleForm("portal.promotion.promote.remove.title",
      annotationRoutes.removeAnnotationPromotionPost(id, context)))
  }

  def removeAnnotationPromotionPost(id: String, context: AnnotationContext.Value) = removePromotionPostAction(id) {
      updated => implicit userOpt => implicit request =>
    annotationResponse(updated, context)
  }

  def demoteAnnotation(id: String, context: AnnotationContext.Value) = promoteAction(id) {
      _ => implicit userOpt => implicit request =>
    Ok(p.helpers.simpleForm("portal.promotion.demote.title",
      annotationRoutes.demoteAnnotationPost(id, context)))
  }

  def demoteAnnotationPost(id: String, context: AnnotationContext.Value) = demotePostAction(id) {
      updated => implicit userOpt => implicit request =>
    annotationResponse(updated, context)
  }

  def removeAnnotationDemotion(id: String, context: AnnotationContext.Value) = promoteAction(id) {
      _ => implicit userOpt => implicit request =>
    Ok(p.helpers.simpleForm("portal.promotion.demote.remove.title",
      annotationRoutes.removeAnnotationDemotionPost(id, context)))
  }

  def removeAnnotationDemotionPost(id: String, context: AnnotationContext.Value) = removeDemotionPostAction(id) {
      updated => implicit userOpt => implicit request =>
    annotationResponse(updated, context)
  }

  /**
   * Convert a contribution visibility value to the correct
   * accessors for the backend
   */
  private def getAccessors(ann: AnnotationF, user: UserProfile)(implicit request: Request[AnyContent]): List[String] = {
    val default: List[String] = utils.ContributionVisibility.form.bindFromRequest.fold(
      errForm => List(user.id), {
        case ContributionVisibility.Me => List(user.id)
        case ContributionVisibility.Groups => user.groups.map(_.id)
        case ContributionVisibility.Custom => {
          VisibilityForm.form.bindFromRequest.fold(
            err => List(user.id), // default to user visibility.
            list => list ::: List(user.id)
          )
        }
      }
    )
    val withMods = if (ann.isPromotable) default ::: getModerators else default
    withMods.distinct
  }

  private def optionalConfigList(key: String)(implicit app: play.api.Application): List[String] = {
    import scala.collection.JavaConverters._
    app.configuration.getStringList(key).map(_.asScala.toList).getOrElse(Nil)
  }

  private def getModerators: List[String] = {
    val all = optionalConfigList("ehri.portal.moderators.all")
    val typed = optionalConfigList(s"ehri.portal.moderators.${EntityType.Annotation}")
    all ++ typed
  }

  /**
   * Convert accessors to contribution visibility enum var...
   */
  private def getContributionVisibility(annotation: Annotation, user: UserProfile): ContributionVisibility.Value = {
    annotation.accessors.map(_.id).sorted match {
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

    val cypher =
      """
        |START n=node:entities(__ID__ = {user})
        |MATCH n -[:belongsTo*]-> g <-[:belongsTo]- u
        |WHERE u.__ID__ <> {user}
        |RETURN DISTINCT u.__ID__, u.name
      """.stripMargin

    (new CypherDAO).cypher(cypher,
        Map("user" -> JsString(user.id),
          "label" -> JsString(Ontology.ACCESSOR_BELONGS_TO_GROUP))).map { json =>
        val users: Seq[(String,String)] = json.as[List[(String,String)]](
          (__ \ "data").read[List[List[String]]].map(all =>  all.map(l => (l(0), l(1))))
        )

      f(users)(user.groups.map(g => (g.id, g.model.name)))
    }
  }
}
