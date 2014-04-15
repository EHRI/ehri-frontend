package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import controllers.base.{SessionPreferences, AuthController, ControllerHelpers}
import models.{AnnotationF, Annotation, UserProfile}
import views.html.p
import utils.{SessionPrefs, ContributionVisibility}
import scala.concurrent.Future.{successful => immediate}
import defines.{ContentTypes, PermissionType}
import backend.rest.cypher.CypherDAO
import play.api.libs.json.Json
import eu.ehri.project.definitions.Ontology
import scala.concurrent.Future
import play.api.mvc.Result
import forms.VisibilityForm
import com.google.common.net.HttpHeaders

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalAnnotations {
  self: Controller with ControllerHelpers with AuthController with SessionPreferences[SessionPrefs] =>

  private val portalRoutes = controllers.portal.routes.Portal

  def annotation(id: String) = userProfileAction.async { implicit userProfile => implicit request =>
    backend.get[Annotation](id).map { ann =>
      Ok(Json.toJson(ann)(Annotation.Converter.clientFormat))
    }
  }

  // Ajax
  def annotate(id: String, did: String) = withUserAction.async {  implicit user => implicit request =>
    getCanShareWith(user) { users => groups =>
      Ok(
        p.common.createAnnotation(
          Annotation.form.bindFromRequest,
          ContributionVisibility.form.bindFromRequest,
          VisibilityForm.form.bindFromRequest,
          portalRoutes.annotatePost(id, did),
          users, groups
        )
      )
    }
  }

  // Ajax
  def annotatePost(id: String, did: String) = withUserAction.async { implicit user => implicit request =>
    Annotation.form.bindFromRequest.fold(
      errorForm => immediate(BadRequest(errorForm.errorsAsJson)),
      ann => {
        val accessors: List[String] = getAccessors(user)
        backend.createAnnotationForDependent(id, did, ann, accessors).map { ann =>
          Created(p.common.annotationBlock(ann, editable = true))
            .withHeaders(
                HttpHeaders.LOCATION -> portalRoutes.annotation(ann.id).url)
        }
      }
    )
  }

  // Ajax
  def editAnnotation(aid: String, isField: Boolean) = withItemPermission.async[Annotation](aid, PermissionType.Update, ContentTypes.Annotation) {
      item => implicit userOpt => implicit request =>
    val vis = getContributionVisibility(item, userOpt.get)
    getCanShareWith(userOpt.get) { users => groups =>
      Ok(p.common.editAnnotation(Annotation.form.fill(item.model),
        ContributionVisibility.form.fill(vis),
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        portalRoutes.editAnnotationPost(aid, isField),
        users, groups))
    }
  }

  def editAnnotationPost(aid: String, isField: Boolean) = withItemPermission.async[Annotation](aid, PermissionType.Update, ContentTypes.Annotation) {
      item => implicit userOpt => implicit request =>
    // save an override field, becuase it's not possible to change it.
    val field = item.model.field
    Annotation.form.bindFromRequest.fold(
      errForm => immediate(BadRequest(errForm.errorsAsJson)),
      edited => {
        backend.update[Annotation,AnnotationF](aid, edited.copy(field = field)).map { done =>
          if (isField) Ok(p.common.annotationInline(done, editable = true))
          else Ok(p.common.annotationBlock(done, editable = true))
        }
      }
    )
  }

  def setAnnotationVisibilityPost(aid: String) = withItemPermission.async[Annotation](aid, PermissionType.Update, ContentTypes.Annotation) {
      item => implicit userOpt => implicit request =>
    val accessors = getAccessors(userOpt.get)
    backend.setVisibility[Annotation](aid, accessors).map { ann =>
      Ok(Json.toJson(ann.accessors.map(_.id)))
    }
  }

  // Ajax
  def deleteAnnotation(aid: String) = withItemPermission[Annotation](aid, PermissionType.Delete, ContentTypes.Annotation) {
      item => implicit userOpt => implicit request =>
    ???
  }

  def deleteAnnotationPost(aid: String) = withItemPermission.async[Annotation](aid, PermissionType.Delete, ContentTypes.Annotation) {
      item => implicit userOpt => implicit request =>
    backend.delete[Annotation](aid).map { done =>
      Ok(true.toString)
    }
  }

  // Ajax
  def annotateField(id: String, did: String, field: String) = withUserAction.async { implicit user => implicit request =>
    getCanShareWith(user) { users => groups =>
      Ok(p.common.createAnnotation(
        Annotation.form.bindFromRequest,
        ContributionVisibility.form.bindFromRequest,
        VisibilityForm.form.bindFromRequest,
        portalRoutes.annotateFieldPost(id, did, field),
        users, groups
      )
      )
    }
  }

  // Ajax
  def annotateFieldPost(id: String, did: String, field: String) = withUserAction.async { implicit user => implicit request =>
    Annotation.form.bindFromRequest.fold(
      errorForm => immediate(BadRequest(errorForm.errorsAsJson)),
      ann => {
        // Add the field to the model!
        val fieldAnn = ann.copy(field = Some(field))
        val accessors: List[String] = getAccessors(user)
        backend.createAnnotationForDependent(id, did, fieldAnn, accessors).map { ann =>
          Created(p.common.annotationInline(ann, editable = true))
            .withHeaders(
              HttpHeaders.LOCATION -> portalRoutes.annotation(ann.id).url)
        }
      }
    )
  }

  /**
   * Convert a contribution visibility value to the correct
   * accessors for the backend
   */
  private def getAccessors(user: UserProfile)(implicit request: Request[AnyContent]): List[String] = {
    utils.ContributionVisibility.form.bindFromRequest.fold(
      errForm => List(user.id), {
        case ContributionVisibility.Me => List(user.id)
        case ContributionVisibility.Groups => user.groups.map(_.id)
        case ContributionVisibility.Custom => {
          VisibilityForm.form.bindFromRequest.fold(
            err => List(user.id), // default to user visibility.
            list => (list ::: List(user.id)).distinct
          )
        }
      }
    )
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
