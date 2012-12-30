package controllers.base

import play.api.libs.concurrent.execution.defaultContext
import models.base.AccessibleEntity
import play.api.mvc.RequestHeader
import play.api.mvc.Call
import models.UserProfile

/**
 * Controller trait which handles the listing and showing of Entities that
 * implement the AccessibleEntity trait.
 *
 * @tparam T
 */
trait EntityRead[T <: AccessibleEntity] extends EntityController[T] {
  val DEFAULT_LIMIT = 20

  type ShowViewType = (T, Option[UserProfile], RequestHeader) => play.api.templates.Html
  type ListViewType = (rest.Page[T], String => Call, Option[UserProfile], RequestHeader) => play.api.templates.Html
  val listView: ListViewType
  val listAction: (Int, Int) => Call
  val showAction: String => Call
  val showView: ShowViewType

  def getJson(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      import com.codahale.jerkson.Json
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
          itemOrErr.right.map {
            item => Ok(Json.generate(item.data))
          }
        }
      }
  }

  def get(id: String) = itemPermissionAction(id) { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
          itemOrErr.right.map {
            item => Ok(showView(builder(item), maybeUser, request))
          }
        }
      }
  }

  def list(page: Int = 1, limit: Int = DEFAULT_LIMIT) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser)
          .page(math.max(page, 1), math.max(limit, 1)).map { itemOrErr =>
          itemOrErr.right.map {
            lst => Ok(listView(lst.copy(list = lst.list.map(builder(_))), showAction, maybeUser, request))
          }
        }
      }
  }
}
