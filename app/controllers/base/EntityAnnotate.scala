package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import models.base.Persistable
import defines._
import models.{Annotation, Entity, UserProfile}
import play.api.data.Form
import models.AnnotationF
import rest.{RestPageParams, EntityDAO}
import collection.immutable.ListMap
import controllers.ListParams

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
}

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam T the entity's build class
 */
trait EntityAnnotate[T <: AnnotatableEntity] extends EntityRead[T] {

  def annotationAction(id: String)(f: models.Entity => Form[AnnotationF] => Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      f(item)(models.AnnotationForm.form.bindFromRequest.discardingErrors)(userOpt)(request)
    }
  }

  def annotationPostAction(id: String)(f: Either[Form[AnnotationF],Annotation] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      models.AnnotationForm.form.bindFromRequest.fold(
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

  def linkSelectAction(id: String, toType: String)(f: AnnotatableEntity => rest.Page[AnnotatableEntity] => ListParams => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
      val linkSrcEntityType = EntityType.withName(toType)
      AnnotatableEntity.fromEntity(item).map { ann =>
        val params = ListParams.bind(request)

        // Need to process params!

        val rp = params.toRestParams(EntityAnnotate.listFilterMappings, EntityAnnotate.orderMappings, Some(EntityAnnotate.DEFAULT_SORT))

        AsyncRest {
          EntityDAO(linkSrcEntityType, userOpt).page(rp).map { pageOrErr =>
            pageOrErr.right.map { page =>
              f(ann)(page.copy(items = page.items.flatMap(e => AnnotatableEntity.fromEntity(e))))(params)(userOpt)(request)
            }
          }
        }
      } getOrElse {
        NotFound(views.html.errors.itemNotFound())
      }
    }
  }

  def linkAction(id: String, toType: String, to: String)(f: AnnotatableEntity => AnnotatableEntity => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
      getEntity(EntityType.withName(toType), to) { srcitem =>
        // If neither items are annotatable throw a 404
        val res: Option[Result] = for {
          target <- AnnotatableEntity.fromEntity(item)
          source <- AnnotatableEntity.fromEntity(srcitem)
        } yield {
            f(target)(source)(userOpt)(request)
        }
        res.getOrElse(NotFound(views.html.errors.itemNotFound()))
      }
    }
  }

  def linkPostAction(id: String, toType: String, to: String)(
      f: Either[(AnnotatableEntity,AnnotatableEntity,Form[AnnotationF]),Annotation] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      models.AnnotationForm.form.bindFromRequest.fold(
        errorForm => { // oh dear, we have an error...
          getEntity(EntityType.withName(toType), to) { srcitem =>
          // If neither items are annotatable throw a 404
            val res: Option[Result] = for {
              target <- AnnotatableEntity.fromEntity(item)
              source <- AnnotatableEntity.fromEntity(srcitem)
            } yield {
              f(Left((target,source,errorForm)))(userOpt)(request)
            }
            res.getOrElse(NotFound(views.html.errors.itemNotFound()))
          }
        },
        ann => {
          AsyncRest {
            rest.AnnotationDAO(userOpt).link(id, to, ann).map { annOrErr =>
              annOrErr.right.map { ann =>
                f(Right(ann))(userOpt)(request)
              }
            }
          }
        }
      )
    }
  }
}

