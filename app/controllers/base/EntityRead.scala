package controllers.base

import play.api.libs.concurrent.execution.defaultContext
import models.base.AccessibleEntity
import play.api.mvc.RequestHeader
import play.api.mvc.Call


trait EntityRead[T <: AccessibleEntity] extends EntityController[T] {

  type ShowViewType = (T, Option[models.sql.User], Option[models.ItemPermissionSet[_]], RequestHeader) => play.api.templates.Html
  type ListViewType = (rest.Page[T], String => Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val listView: ListViewType
  val listAction: (Int, Int) => Call
  val showAction: String => Call
  val showView: ShowViewType

  import com.codahale.jerkson.Json
  
  def getJson(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item => Ok(Json.generate(item.data)) }
        }
      }
  }

  def get(id: String) = itemPermAction(id) { implicit maybeUser =>
    implicit maybePerms =>
    implicit request =>
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item => Ok(showView(builder(item), maybeUser, maybePerms, request)) }
        }
      }
  }

  def list(page: Int, limit: Int) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser.flatMap(_.profile))
          .page(math.max(page, 1), math.max(limit, 1)).map { itemOrErr =>
            itemOrErr.right.map { lst => Ok(listView(lst.copy(list = lst.list.map(builder(_))), showAction, maybeUser, request)) }
          }
      }
  }
}
