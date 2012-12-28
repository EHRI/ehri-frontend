package controllers.base

import play.api.libs.concurrent.execution.defaultContext
import models.base._
import models.base.Persistable
import defines._
import models.UserProfile

trait VisibilityController[F <: Persistable, T <: AccessibleEntity with Formable[F]] extends EntityRead[T] {

  import play.api.mvc.Call
  import play.api.mvc.RequestHeader

  val visibilityAction: String => Call
  val setVisibilityAction: String => Call
  type VisibilityViewType = (Accessor, List[(String, String)], List[(String, String)], Call, UserProfile, RequestHeader) => play.api.templates.Html
  val visibilityView: VisibilityViewType


  def visibility(id: String) = withItemPermission(id, PermissionType.Update) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        for {
          itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
          users <- rest.RestHelpers.getUserList
          groups <- rest.RestHelpers.getGroupList
        } yield {
          itemOrErr.right.map { item =>
            Ok(views.html.visibility(builder(item), users, groups, setVisibilityAction(id), user, request))
          }
        }
      }
  }

  def visibilityPost(id: String) = withItemPermission(id, PermissionType.Update) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      val data = request.body.asFormUrlEncoded.getOrElse(List()).flatMap { case (_, s) => s.toList }
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
          itemOrErr.right.map { item =>
            val model = builder(item)
            AsyncRest {
              rest.VisibilityDAO(user).set(model, data.toList).map { boolOrErr =>
                boolOrErr.right.map { bool =>
                  Redirect(showAction(id))
                }
              }
            }
          }
        }
      }
  }
}

