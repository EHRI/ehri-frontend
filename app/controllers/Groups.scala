package controllers

import models.{ PermissionSet, Group, GroupRepr }
import models.base.Accessor
import models.base.AccessibleEntity
import defines._
import play.api.libs.concurrent.execution.defaultContext
import rest.{ EntityDAO, PermissionDAO }
import controllers.base.{ CRUD, EntityController }
import models.Persistable
import play.api.libs.json.JsValue
import models.base.Formable
import play.api.libs.ws.WS
import controllers.base.EntityRead

object Groups extends AccessorController[Group, GroupRepr] 
		with VisibilityController[Group,GroupRepr]
		with CRUD[Group, GroupRepr] {
  val entityType = EntityType.Group
  val listAction = routes.Groups.list _
  val createAction = routes.Groups.createPost
  val updateAction = routes.Groups.updatePost _
  val cancelAction = routes.Groups.get _
  val deleteAction = routes.Groups.deletePost _
  val permsAction = routes.Groups.permissions _
  val setPermsAction = routes.Groups.permissionsPost _
  
  val setVisibilityAction = routes.Groups.visibilityPost _
  val visibilityAction = routes.Groups.visibility _
  val visibilityView = views.html.visibility.apply _  
  
  val form = forms.GroupForm.form
  val showAction = routes.Groups.get _
  val formView = views.html.group.edit.apply _
  val showView = views.html.group.show.apply _
  val listView = views.html.list.apply _
  val deleteView = views.html.delete.apply _
  val permView = views.html.permissions.edit.apply _
  val builder = GroupRepr
}

trait VisibilityController[F <: Persistable, T <: AccessibleEntity with Formable[F]] extends EntityRead[F, T] {

  import play.api.mvc.Call
  import play.api.mvc.RequestHeader

  val visibilityAction: String => Call
  val setVisibilityAction: String => Call
  type VisibilityViewType = (Accessor, List[(String, String)], List[(String, String)], Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val visibilityView: VisibilityViewType

  import play.api.libs.json.Reads
  import play.api.libs.json.Reads._
  import play.api.libs.json.util._
  import play.api.libs.json._

  def parseUsers(json: JsValue): List[(String, String)] = {
    (json \ "data").as[List[List[String]]].flatMap { lst =>
      lst match {
        case x :: y :: _ => Some((x, y))
        case _ => None
      }
    }
  }

  def visibility(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      maybeUser.flatMap(_.profile).map { userProfile =>
        AsyncRest {
          for {
            itemOrErr <- EntityDAO(entityType, Some(userProfile)).get(id)
            usersOrErr <- rest.cypher.CypherDAO()
              .cypher("START n=node:%s('identifier:*') RETURN n.identifier, n.name".format(EntityType.UserProfile))
            groupsOrErr <- rest.cypher.CypherDAO()
              .cypher("START n=node:%s('identifier:*') RETURN n.identifier, n.name".format(EntityType.Group))
          } yield {
            if (usersOrErr.isLeft) sys.error("Unable to fetch user list: " + usersOrErr.left.get)
            if (groupsOrErr.isLeft) sys.error("Unable to fetch user list: " + groupsOrErr.left.get)

            val users = parseUsers(usersOrErr.right.get)
            val groups = parseUsers(groupsOrErr.right.get)

            itemOrErr.right.map { item =>
              Ok(views.html.visibility(builder(item), users, groups, setVisibilityAction(id), maybeUser, request))
            }
          }
        }
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }

  def visibilityPost(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      maybeUser.flatMap(_.profile).map { userProfile =>
        val data = request.body.asFormUrlEncoded.getOrElse(Map()).map { case (i, s) => (i, s.toList) }
        AsyncRest {
          EntityDAO(entityType, Some(userProfile)).get(id).map { itemOrErr =>
            itemOrErr.right.map { item =>
              val model = builder(item)
              AsyncRest {
                rest.VisibilityDAO(userProfile).set(model, data).map { boolOrErr =>
                  boolOrErr.right.map { bool =>
                    Redirect(showAction(id))
                  }
                }
              }
            }
          }
        }
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }
}

trait AccessorController[F <: Persistable, T <: Accessor] extends EntityController[F, T] {

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
