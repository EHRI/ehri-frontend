package controllers.portal.annotate

import auth.AccountManager
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import controllers.generic.{Search, Read, Promotion, Visibility}
import models.{AnnotationF, Annotation, UserProfile}
import utils.ContributionVisibility
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
import utils.search.{SearchItemResolver, SearchEngine}
import models.view.AnnotationContext


import javax.inject._
import controllers.portal.FacetConfig
import controllers.portal.base.PortalController

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Annotations @Inject()(implicit app: play.api.Application, cache: CacheApi, globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver,
                                 backend: Backend, accounts: AccountManager, pageRelocator: utils.MovedPageLookup, messagesApi: MessagesApi)
  extends PortalController
  with Read[Annotation]
  with Visibility[Annotation]
  with Promotion[Annotation]
  with Search
  with FacetConfig {

  private val annotationRoutes = controllers.portal.annotate.routes.Annotations

  private val annotationDefaults: Map[String,String] = Map(
    AnnotationF.IS_PRIVATE -> true.toString
  )

  def searchAll = OptionalUserAction.async { implicit request =>
    find[Annotation](
      entities = List(EntityType.Annotation),
      facetBuilder = annotationFacets
    ).map { result =>
      Ok(views.html.annotation.list(result, annotationRoutes.searchAll()))
    }
  }

  def browse(id: String) = OptionalUserAction.async { implicit request =>
    userBackend.get[Annotation](id).map { ann =>
      if (isAjax) Ok(Json.toJson(ann)(client.json.annotationJson.clientFormat))
      else Ok(views.html.annotation.show(ann))
    }
  }

  // Ajax
  def annotate(id: String, did: String) = WithUserAction.async {  implicit request =>
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
  def annotatePost(id: String, did: String) = WithUserAction.async { implicit request =>
    Annotation.form.bindFromRequest.fold(
      errorForm => immediate(BadRequest(errorForm.errorsAsJson)),
      ann => {
        val accessors: Seq[String] = getAccessors(ann, request.user)
        userBackend.createAnnotationForDependent[Annotation,AnnotationF](id, did, ann, accessors).map { ann =>
          Created(views.html.annotation.annotationBlock(ann, editable = true))
            .withHeaders(
                HttpHeaders.LOCATION -> annotationRoutes.browse(ann.id).url)
        }
      }
    )
  }

  // Ajax
  def editAnnotation(aid: String, context: AnnotationContext.Value) = {
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

  def editAnnotationPost(aid: String, context: AnnotationContext.Value) = {
    WithItemPermissionAction(aid, PermissionType.Update).async { implicit request =>
      // save an override field, becuase it's not possible to change it.
      val field = request.item.model.field
      Annotation.form.bindFromRequest.fold(
        errForm => immediate(BadRequest(errForm.errorsAsJson)),
        edited => userBackend.update[Annotation,AnnotationF](aid, edited.copy(field = field)).flatMap { updated =>
          // Because the user might have marked this item
          // private (removing the isPromotable flag) we need to
          // recalculate who can access it.
          val newAccessors = getAccessors(updated.model, request.userOpt.get)
          if (newAccessors.sorted == updated.accessors.map(_.id).sorted)
            immediate(annotationResponse(updated, context))
          else userBackend.setVisibility[Annotation](aid, newAccessors).map { ann =>
            annotationResponse(ann, context)
          }
        }
      )
    }
  }

  def setAnnotationVisibilityPost(aid: String) = {
    WithItemPermissionAction(aid, PermissionType.Update).async { implicit request =>
      val accessors = getAccessors(request.item.model, request.userOpt.get)
      userBackend.setVisibility[Annotation](aid, accessors).map { ann =>
        Ok(Json.toJson(ann.accessors.map(_.id)))
      }
    }
  }

  // Ajax
  def deleteAnnotation(aid: String) = WithItemPermissionAction(aid, PermissionType.Delete).apply { implicit request =>
      Ok(views.html.helpers.simpleForm("annotation.delete.title",
          annotationRoutes.deleteAnnotationPost(aid)))
  }

  def deleteAnnotationPost(aid: String) = WithItemPermissionAction(aid, PermissionType.Delete).async { implicit request =>
    userBackend.delete[Annotation](aid).map { done =>
      Ok(true.toString)
    }
  }

  // Ajax
  def annotateField(id: String, did: String, field: String) = WithUserAction.async { implicit request =>
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
  def annotateFieldPost(id: String, did: String, field: String) = WithUserAction.async { implicit request =>
    Annotation.form.bindFromRequest.fold(
      errorForm => immediate(BadRequest(errorForm.errorsAsJson)),
      ann => {
        // Add the field to the model!
        val fieldAnn = ann.copy(field = Some(field))
        val accessors: Seq[String] = getAccessors(ann, request.user)
        userBackend.createAnnotationForDependent[Annotation,AnnotationF](id, did, fieldAnn, accessors).map { ann =>
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

  def promoteAnnotation(id: String, context: AnnotationContext.Value) = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.helpers.simpleForm("promotion.promote.title",
      annotationRoutes.promoteAnnotationPost(id, context)))
  }

  def promoteAnnotationPost(id: String, context: AnnotationContext.Value) = PromoteItemAction(id).apply { implicit request =>
    annotationResponse(request.item, context)
  }

  def removeAnnotationPromotion(id: String, context: AnnotationContext.Value) = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.helpers.simpleForm("promotion.promote.remove.title",
      annotationRoutes.removeAnnotationPromotionPost(id, context)))
  }

  def removeAnnotationPromotionPost(id: String, context: AnnotationContext.Value) = RemovePromotionAction(id).apply { implicit request =>
    annotationResponse(request.item, context)
  }

  def demoteAnnotation(id: String, context: AnnotationContext.Value) = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.helpers.simpleForm("promotion.demote.title",
      annotationRoutes.demoteAnnotationPost(id, context)))
  }

  def demoteAnnotationPost(id: String, context: AnnotationContext.Value) = DemoteItemAction(id).apply { implicit request =>
    annotationResponse(request.item, context)
  }

  def removeAnnotationDemotion(id: String, context: AnnotationContext.Value) = PromoteItemAction(id).apply { implicit request =>
    Ok(views.html.helpers.simpleForm("promotion.demote.remove.title",
      annotationRoutes.removeAnnotationDemotionPost(id, context)))
  }

  def removeAnnotationDemotionPost(id: String, context: AnnotationContext.Value) = RemoveDemotionAction(id).apply { implicit request =>
    annotationResponse(request.item, context)
  }

  /**
   * Convert a contribution visibility value to the correct
   * accessors for the backend
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
