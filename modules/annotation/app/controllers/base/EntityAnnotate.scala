package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import defines._
import models._
import play.api.data.Form
import rest.{BadJson, AnnotationDAO}
import models.forms.AnnotationForm
import play.api.libs.json.{Format, Json, JsError}
import models.json.RestReadable


object EntityAnnotate {
  // Create a format for client read/writes
  implicit val annotationTypeFormat = defines.EnumUtils.enumFormat(AnnotationF.AnnotationType)
  implicit val clientAnnotationFormat: Format[AnnotationF] = Json.format[AnnotationF]
}

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam MT the entity's build class
 */
trait EntityAnnotate[MT] extends EntityRead[MT] {

  def annotationAction(id: String)(f: MT => Form[AnnotationF] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]): Action[AnyContent] = {
    withItemPermission[MT](id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      f(item)(AnnotationForm.form.bindFromRequest)(userOpt)(request)
    }
  }

  def annotationPostAction(id: String)(f: Either[Form[AnnotationF],Annotation] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      AnnotationForm.form.bindFromRequest.fold(
        errorForm => f(Left(errorForm))(userOpt)(request),
        ann => {
          AsyncRest {
            rest.AnnotationDAO().create(id, ann).map { annOrErr =>
              annOrErr.right.map { ann =>
                f(Right(ann))(userOpt)(request)
              }
            }
          }
        }
      )
    }
  }

  /**
   * Fetch annotations for a given item.
   */
  def getAnnotationsAction(id: String)(
      f: Map[String,List[Annotation]] => Option[UserProfile] => Request[AnyContent] => Result) = {
    userProfileAction { implicit  userOpt => implicit request =>
      AsyncRest {
        val annsReq = rest.AnnotationDAO().getFor(id)
        for (annOrErr <- annsReq) yield {
          for { anns <- annOrErr.right } yield {
            f(anns)(userOpt)(request)
          }
        }
      }
    }
  }

  //
  // JSON endpoints
  //

  import EntityAnnotate._

  def getAnnotationJson(id: String) = getAnnotationsAction(id) {
      anns => implicit userOpt => implicit request =>
    Ok(Json.toJson(anns.map{ case (itemId, anns) =>
      itemId -> anns.map(_.model)
    }))
  }

  /**
   * Create an annotation via Ajax...
   *
   * @param id The item's id
   * @return
   */
  def createAnnotationJsonPost(id: String) = Action(parse.json) { request =>
    request.body.validate[AnnotationF](clientAnnotationFormat).fold(
      errors => { // oh dear, we have an error...
        BadRequest(JsError.toFlatJson(errors))
      },
      ap => {
        // NB: No checking of permissions here - we're going to depend
        // on the server for that
        userProfileAction { implicit userOpt => implicit request =>
          AsyncRest {
            rest.AnnotationDAO().create(id, ap).map { annOrErr =>
              annOrErr.right.map { ann =>
                Created(Json.toJson(ann.model)(clientAnnotationFormat))
              }
            }
          }
        }(request.map(js => AnyContentAsEmpty))
      }
    )
  }
}

