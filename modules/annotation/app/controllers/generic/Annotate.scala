package controllers.generic

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import defines._
import models._
import play.api.data.Form
import play.api.libs.json.{Format, Json, JsError}
import scala.concurrent.Future.{successful => immediate}
import backend.{BackendReadable, BackendContentType}


object Annotate {
  // Create a format for client read/writes
  implicit val annotationTypeFormat = defines.EnumUtils.enumFormat(AnnotationF.AnnotationType)
  implicit val clientAnnotationFormat: Format[AnnotationF] = Json.format[AnnotationF]
}

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam MT the entity's build class
 */
trait Annotate[MT] extends Read[MT] {

  import Annotate._

  def annotationAction(id: String)(f: MT => Form[AnnotationF] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]): Action[AnyContent] = {
    withItemPermission[MT](id, PermissionType.Annotate) { item => implicit userOpt => implicit request =>
      f(item)(Annotation.form.bindFromRequest)(userOpt)(request)
    }
  }

  def annotationPostAction(id: String)(f: Either[Form[AnnotationF],Annotation] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Annotate) { item => implicit userOpt => implicit request =>
      Annotation.form.bindFromRequest.fold(
        errorForm => immediate(f(Left(errorForm))(userOpt)(request)),
        ann => backend.createAnnotation(id, ann).map { ann =>
          f(Right(ann))(userOpt)(request)
        }
      )
    }
  }

  /**
   * Fetch annotations for a given item.
   */
  def getAnnotationsAction(id: String)(
      f: Seq[Annotation] => Option[UserProfile] => Request[AnyContent] => Result) = {
    userProfileAction.async { implicit  userOpt => implicit request =>
      backend.getAnnotationsForItem(id).map { anns =>
        f(anns)(userOpt)(request)
      }
    }
  }

  /**
   * Create an annotation via Ajax...
   *
   * @param id The item's id
   * @return
   */
  def createAnnotationJsonPost(id: String) = Action.async(parse.json) { request =>
    request.body.validate[AnnotationF](clientAnnotationFormat).fold(
      errors => immediate(BadRequest(JsError.toFlatJson(errors))),
      ap => {
        // NB: No checking of permissions here - we're going to depend
        // on the server for that
        userProfileAction.async { implicit userOpt => implicit request =>
          backend.createAnnotation(id, ap).map { ann =>
            Created(Json.toJson(ann.model)(clientAnnotationFormat))
          }
        }(request.map(js => AnyContentAsEmpty))
      }
    )
  }
}

