package controllers.generic

import backend.{BackendReadable, BackendContentType}
import defines.PermissionType
import play.api.data.Form
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models._
import play.api.libs.json.{Json, JsError}
import scala.concurrent.Future.{successful => immediate}

/**
 * Helper actions for controllers that can annotate item types.
 *
 * @tparam MT the entity's generic type
 */
trait Annotate[MT] extends Read[MT] {

  def annotationAction(id: String)(f: MT => Form[AnnotationF] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]): Action[AnyContent] = {
    WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
      f(request.item)(Annotation.form.bindFromRequest)(request.userOpt)(request)
    }
  }

  @deprecated(message = "Use endpoints in Annotations controller instead", since = "1.0.2")
  def annotationPostAction(id: String)(f: Either[Form[AnnotationF],Annotation] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    WithItemPermissionAction(id, PermissionType.Annotate).async { implicit request =>
      Annotation.form.bindFromRequest.fold(
        errorForm => immediate(f(Left(errorForm))(request.userOpt)(request)),
        ann => backend.createAnnotation[Annotation,AnnotationF](id, ann).map { ann =>
          f(Right(ann))(request.userOpt)(request)
        }
      )
    }
  }

  /**
   * Fetch annotations for a given item.
   */
  @deprecated(message = "Use ItemMetaAction instead", since = "1.0.2")
  def getAnnotationsAction(id: String)(
      f: Seq[Annotation] => Option[UserProfile] => Request[AnyContent] => Result) = {
    OptionalUserAction.async { implicit request =>
      backend.getAnnotationsForItem[Annotation](id).map { anns =>
        f(anns)(request.userOpt)(request)
      }
    }
  }



  /**
   * Create an annotation via Ajax... note that this is an action
   * in itself and not a builder.
   *
   * @param id The item's id
   * @return
   */
  def createAnnotationJsonPost(id: String) = Action.async(parse.json) { request =>
    request.body.validate[AnnotationF](AnnotationF.Converter.clientFormat).fold(
      errors => immediate(BadRequest(JsError.toFlatJson(errors))),
      ap => {
        // NB: No checking of permissions here - we're going to depend
        // on the server for that
        OptionalUserAction.async { implicit request =>
          backend.createAnnotation[Annotation,AnnotationF](id, ap).map { ann =>
            Created(Json.toJson(ann.model)(AnnotationF.Converter.clientFormat))
          }
        }(request.map(js => AnyContentAsEmpty))
      }
    )
  }
}

