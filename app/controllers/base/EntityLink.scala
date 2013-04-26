package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import models.base.Persistable
import defines._
import models._
import play.api.data.Form
import rest.{RestError, RestPageParams, EntityDAO}
import collection.immutable.ListMap
import controllers.ListParams
import models.forms.LinkForm
import scala.concurrent.Future


/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam T the entity's build class
 */
trait EntityLink[T <: LinkableEntity] extends EntityRead[T] {

  def linkSelectAction(id: String, toType: String)(f: LinkableEntity => rest.Page[LinkableEntity] => ListParams => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
      val linkSrcEntityType = EntityType.withName(toType)
      LinkableEntity.fromEntity(item).map { ann =>
        val params = ListParams.bind(request)

        // Need to process params!

        val rp = params.toRestParams(EntityAnnotate.listFilterMappings, EntityAnnotate.orderMappings, Some(EntityAnnotate.DEFAULT_SORT))

        AsyncRest {
          EntityDAO(linkSrcEntityType, userOpt).page(rp).map { pageOrErr =>
            pageOrErr.right.map { page =>
              f(ann)(page.copy(items = page.items.flatMap(e => LinkableEntity.fromEntity(e))))(params)(userOpt)(request)
            }
          }
        }
      } getOrElse {
        NotFound(views.html.errors.itemNotFound())
      }
    }
  }

  def linkAction(id: String, toType: String, to: String)(f: LinkableEntity => LinkableEntity => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
      getEntity(EntityType.withName(toType), to) { srcitem =>
        // If neither items are annotatable throw a 404
        val res: Option[Result] = for {
          target <- LinkableEntity.fromEntity(item)
          source <- LinkableEntity.fromEntity(srcitem)
        } yield {
            f(target)(source)(userOpt)(request)
        }
        res.getOrElse(NotFound(views.html.errors.itemNotFound()))
      }
    }
  }

  def linkPostAction(id: String, toType: String, to: String)(
      f: Either[(LinkableEntity, LinkableEntity,Form[LinkF]),Link] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
      LinkForm.form.bindFromRequest.fold(
        errorForm => { // oh dear, we have an error...
          getEntity(EntityType.withName(toType), to) { srcitem =>
          // If neither items are annotatable throw a 404
            val res: Option[Result] = for {
              target <- LinkableEntity.fromEntity(item)
              source <- LinkableEntity.fromEntity(srcitem)
            } yield {
              f(Left((target,source,errorForm)))(userOpt)(request)
            }
            res.getOrElse(NotFound(views.html.errors.itemNotFound()))
          }
        },
        ann => {
          AsyncRest {
            rest.LinkDAO(userOpt).link(id, to, ann).map { annOrErr =>
              annOrErr.right.map { ann =>
                f(Right(ann))(userOpt)(request)
              }
            }
          }
        }
      )
    }
  }

  def linkMultiAction(id: String)(f: LinkableEntity => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
      val res: Option[Result] = for {
        target <- LinkableEntity.fromEntity(item)
      } yield {
        f(target)(userOpt)(request)
      }
      res.getOrElse(NotFound(views.html.errors.itemNotFound()))
    }
  }

  def linkPostMultiAction(id: String)(
      f: Either[(LinkableEntity,Form[List[(String,LinkF)]]),List[Link]] => Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      println("Form data: " + request.body.asFormUrlEncoded)
      val multiForm: Form[List[(String,LinkF)]] = models.forms.LinkForm.multiForm
      multiForm.bindFromRequest.fold(
        errorForm => { // oh dear, we have an error...
          val res: Option[Result] = for {
            target <- LinkableEntity.fromEntity(item)
          } yield {
            f(Left((target,errorForm)))(userOpt)(request)
          }
          res.getOrElse(NotFound(views.html.errors.itemNotFound()))
        },
        links => {
          AsyncRest {
            val anns: Future[List[Either[RestError,Link]]] = Future.sequence {
              links.map { case (other, linkData) =>
                rest.LinkDAO(userOpt).link(id, other, linkData)
              }
            }
            anns.map { (lst: List[Either[RestError,Link]]) =>
              // If there was an error, pluck the first one out and
              // return it...
              lst.filter(_.isLeft).map(_.left.get).headOption.map { err =>
                Left(err)
              } getOrElse {
                Right(f(Right(lst.map(_.right.get)))(userOpt)(request))
              }
            }
          }
        }
      )
    }
  }
}

