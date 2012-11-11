package controllers.base

import rest._
import play.api.libs.concurrent.execution.defaultContext
import play.api.mvc.Controller
import play.api.data.Form
import play.api.mvc.RequestHeader
import defines._
import play.api.mvc.Call
import models._
import models.base.AccessibleEntity


trait CRUD[F <: BaseModel, T <: AccessibleEntity] extends EntityCreate[F,T] with EntityRead[F,T] with EntityUpdate[F,T] with EntityDelete[F,T]


trait EntityController[F <: BaseModel, T <: AccessibleEntity] extends Controller with AuthController with ControllerHelpers {
  val entityType: EntityType.Value
  def builder: Entity => T
}

trait EntityRead[F <: BaseModel, T <: AccessibleEntity] extends EntityController[F,T] {

  type ShowViewType = (T, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  type ListViewType = (rest.Page[T], String => Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val listView: ListViewType
  val listAction: (Int, Int) => Call
  val showAction: String => Call
  val showView: ShowViewType

  import com.codahale.jerkson.Json
  
  def getJson(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item => Ok(Json.generate(item.data)) }
        }
      }
  }

  def get(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item => Ok(showView(builder(item), maybeUser, request)) }
        }
      }
  }

  def list(page: Int, limit: Int) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(entityType, maybeUser.flatMap(_.profile))
          .page(math.max(page, 1), math.max(limit, 1)).map { itemOrErr =>
            itemOrErr.right.map { lst => Ok(listView(lst.copy(list = lst.list.map(builder(_))), showAction, maybeUser, request)) }
          }
      }
  }
}

trait EntityCreate[F <: BaseModel, T <: AccessibleEntity] extends EntityRead[F,T] {
  type FormViewType = (Option[T], Form[F], Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val createAction: Call
  val formView: FormViewType
  val form: Form[F]

  def create = userProfileAction { implicit maybeUser =>
    implicit request =>
      Ok(formView(None, form, createAction, maybeUser, request))
  }

  def createPost = userProfileAction { implicit maybeUser =>
    implicit request =>
      form.bindFromRequest.fold(
        errorForm => BadRequest(formView(None, errorForm, createAction, maybeUser, request)),
        doc => {
          AsyncRest {
            EntityDAO(entityType, maybeUser.flatMap(_.profile))
              .create(doc.toData).map { itemOrErr =>
                itemOrErr.right.map { item => Redirect(showAction(builder(item).identifier)) }
              }
          }
        }
      )
  }
}

trait EntityUpdate[F <: BaseModel, T <: AccessibleEntity] extends EntityRead[F,T] {
  val updateAction: String => Call
  val formView: EntityCreate[F,T]#FormViewType
  val form: Form[F]

  def update(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item =>
            val doc: T = builder(item)
            // TODO: Work out most economic way of filling form from T
            Ok(formView(Some(doc), form, updateAction(id), maybeUser, request))
          }
        }
      }
  }

  def updatePost(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      form.bindFromRequest.fold(
        errorForm => {
          AsyncRest {
            EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
              itemOrErr.right.map { item =>
                val doc: T = builder(item)
                BadRequest(formView(Some(doc), errorForm, updateAction(id), maybeUser, request))
              }
            }
          }
        },
        doc => {
          AsyncRest {
            EntityDAO(entityType, maybeUser.flatMap(_.profile))
              .update(id, doc.toData).map { itemOrErr =>
                itemOrErr.right.map { item =>
                  Redirect(showAction(builder(item).identifier))
                }
              }
          }
        }
      )
  }
}

trait EntityDelete[F <: BaseModel, T <: AccessibleEntity] extends EntityRead[F,T] {

  type DeleteViewType = (T, Call, Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val deleteAction: String => Call
  val deleteView: DeleteViewType
  val cancelAction: String => Call

  def delete(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item =>
            val doc: T = builder(item)
            Ok(deleteView(doc, deleteAction(id), cancelAction(id), maybeUser, request))

          }
        }
      }
  }

  def deletePost(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(entityType, maybeUser.flatMap(_.profile)).delete(id).map { boolOrErr =>
          boolOrErr.right.map { ok => Redirect(listAction(0, 20)) }
        }
      }
  }
}