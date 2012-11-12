package controllers

import models.{PermissionSet, Group,GroupRepr}
import models.base.Accessor
import models.base.AccessibleEntity
import defines._
import play.api.libs.concurrent.execution.defaultContext
import rest.{EntityDAO,PermissionDAO}
import controllers.base.{CRUD,EntityController}
import models.Persistable


object Groups extends AccessorController[Group,GroupRepr] with CRUD[Group,GroupRepr] {
  val entityType = EntityType.Group
  val listAction = routes.Groups.list _
  val createAction = routes.Groups.createPost
  val updateAction = routes.Groups.updatePost _
  val cancelAction = routes.Groups.get _
  val deleteAction = routes.Groups.deletePost _
  val permsAction = routes.Groups.permissions _
  val setPermsAction = routes.Groups.permissionsPost _
  val form = forms.GroupForm.form
  val showAction = routes.Groups.get _
  val formView = views.html.group.edit.apply _
  val showView = views.html.group.show.apply _
  val listView = views.html.list.apply _
  val deleteView = views.html.delete.apply _
  val permView = views.html.permissions.edit.apply _
  val builder = GroupRepr
}


trait AccessorController[F <: Persistable, T <: Accessor] extends EntityController[F,T] {
  
  import play.api.mvc.Call
  import play.api.mvc.RequestHeader

  val permsAction: String => Call
  val setPermsAction: String => Call
  type PermViewType = (Accessor, PermissionSet[Accessor], Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val permView: PermViewType

  def permissions(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      maybeUser.flatMap(_.profile).map { userProfile =>
        AsyncRest {
          for {
            itemOrErr <- EntityDAO(entityType, Some(userProfile)).get(id)
            permsOrErr <- rest.PermissionDAO(userProfile).get(builder(itemOrErr.right.get))
          } yield {

            permsOrErr.right.map { perms =>
              Ok(permView(builder(itemOrErr.right.get), perms, setPermsAction(id), maybeUser, request))
            }
          }
        }
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }

  def permissionsPost(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: Map[String, List[String]] = ContentType.values.toList.map { ct =>
        (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
      }.toMap

      maybeUser.flatMap(_.profile).map { userProfile =>
        AsyncRest {
          for {
            itemOrErr <- EntityDAO(entityType, Some(userProfile)).get(id)
            newpermsOrErr <- PermissionDAO(userProfile).set(builder(itemOrErr.right.get), perms)
          } yield {
            newpermsOrErr.right.map { perms =>
              Redirect(permsAction(id))
            }
          }
        }
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }
}
