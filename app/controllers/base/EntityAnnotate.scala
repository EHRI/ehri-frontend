package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import models.base.Persistable
import defines._
import models._
import play.api.data.Form
import rest.{AnnotationDAO, RestError, RestPageParams, EntityDAO}
import collection.immutable.ListMap
import controllers.ListParams
import models.forms.{LinkForm, AnnotationForm}
import scala.concurrent.Future
import play.api.libs.json.{Format, Json, JsError}


object EntityAnnotate {
  /**
   * Mapping between incoming list filter parameters
   * and the data values accessed via the server.
   */
  val DEFAULT_SORT = AccessibleEntity.NAME

  val listFilterMappings: ListMap[String,String] = ListMap(
    AccessibleEntity.NAME -> s"<-describes.${AccessibleEntity.NAME}"
  )

  val orderMappings: ListMap[String,String] = ListMap(
    AccessibleEntity.NAME -> AccessibleEntity.NAME
  )



  // Create a format for client read/writes
  implicit val annotationTypeFormat = defines.EnumUtils.enumFormat(AnnotationF.AnnotationType)
  implicit val clientAnnotationFormat: Format[AnnotationF] = Json.format[AnnotationF]
}

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam T the entity's build class
 */
trait EntityAnnotate[T <: AnnotatableEntity] extends EntityRead[T] {

  def annotationAction(id: String)(f: models.Entity => Form[AnnotationF] => Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      f(item)(AnnotationForm.form.bindFromRequest)(userOpt)(request)
    }
  }

  def annotationPostAction(id: String)(f: Either[Form[AnnotationF],Annotation] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      AnnotationForm.form.bindFromRequest.fold(
        errorForm => f(Left(errorForm))(userOpt)(request),
        ann => {
          AsyncRest {
            rest.AnnotationDAO(userOpt).create(id, ann).map { annOrErr =>
              annOrErr.right.map { ann =>
                f(Right(ann))(userOpt)(request)
              }
            }
          }
        }
      )
    }
  }

  //
  // JSON endpoints
  //

  import EntityAnnotate._

  def getAnnotationJson(id: String) = userProfileAction { implicit userOpt => implicit request =>
    AsyncRest {
      AnnotationDAO(userOpt).getFor(id).map { annsOrErr =>
        annsOrErr.right.map { anns =>
          Ok(Json.toJson(anns.map{ case (itemId, anns) =>
            itemId -> anns.map(_.formable)
          }))
        }
      }
    }
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
            rest.AnnotationDAO(userOpt).create(id, ap).map { annOrErr =>
              annOrErr.right.map { ann =>
                Created(Json.toJson(ann.formable)(clientAnnotationFormat))
              }
            }
          }
        }(request.map(js => AnyContentAsEmpty))
      }
    )
  }
}

