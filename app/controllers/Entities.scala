package controllers

import com.codahale.jerkson.Json.generate
import rest._
import play.api.libs.concurrent.execution.defaultContext
import play.api.mvc.Controller
import play.api.data.Form
import play.api.mvc.Action
import play.api.mvc.SimpleResult
import play.api.mvc.AnyContent
import play.api.mvc.Result
import scala.concurrent.Future
import play.api.mvc.RequestHeader
import defines.EntityType
import play.api.mvc.Call
import models._

object DocumentaryUnits extends EntityController[DocumentaryUnit] {
  val entityType = EntityType.DocumentaryUnit
  val listAction = routes.DocumentaryUnits.list
  val createAction = routes.DocumentaryUnits.createPost
  val updateAction = routes.DocumentaryUnits.updatePost _
  val cancelAction = routes.DocumentaryUnits.get _
  val deleteAction = routes.DocumentaryUnits.deletePost _
  val form = forms.DocumentaryUnitForm.form
  val showAction = routes.DocumentaryUnits.get _
  val formView = views.html.documentaryUnit.edit.apply _
  val showView = views.html.documentaryUnit.show.apply _
  val listView = views.html.list.apply _
  val deleteView = views.html.delete.apply _
  val builder: (AccessibleEntity => DocumentaryUnit) = DocumentaryUnit.apply _
}

object UserProfiles extends EntityController[UserProfile] {
  val entityType = EntityType.UserProfile
  val listAction = routes.UserProfiles.list
  val createAction = routes.UserProfiles.createPost
  val updateAction = routes.UserProfiles.updatePost _
  val cancelAction = routes.UserProfiles.get _
  val deleteAction = routes.UserProfiles.deletePost _
  val form = forms.UserProfileForm.form
  val showAction = routes.UserProfiles.get _
  val formView = views.html.userProfile.edit.apply _
  val showView = views.html.show.apply _
  val listView = views.html.list.apply _
  val deleteView = views.html.delete.apply _
  val builder: (AccessibleEntity => UserProfile) = UserProfile.apply _
}

object Groups extends EntityController[Group] {
  val entityType = EntityType.Group
  val listAction = routes.Groups.list
  val createAction = routes.Groups.createPost
  val updateAction = routes.Groups.updatePost _
  val cancelAction = routes.Groups.get _
  val deleteAction = routes.Groups.deletePost _
  val form = forms.GroupForm.form
  val showAction = routes.Groups.get _
  val formView = views.html.group.edit.apply _
  val showView = views.html.show.apply _
  val listView = views.html.list.apply _
  val deleteView = views.html.delete.apply _
  val builder: (AccessibleEntity => Group) = Group.apply _
}

object Agents extends EntityController[Agent] {
  val entityType = EntityType.Agent
  val listAction = routes.Agents.list
  val createAction = routes.Agents.createPost
  val updateAction = routes.Agents.updatePost _
  val cancelAction = routes.Agents.get _
  val deleteAction = routes.Agents.deletePost _
  val form = forms.AgentForm.form
  val showAction = routes.Agents.get _
  val formView = views.html.agent.edit.apply _
  val showView = views.html.show.apply _
  val listView = views.html.list.apply _
  val deleteView = views.html.delete.apply _
  val builder: (AccessibleEntity => Agent) = Agent.apply _
}

trait EntityController[T <: ManagedEntity] extends Controller with AuthController with ControllerHelpers {

  val entityType: EntityType.Value
  type ShowViewType = (T, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  type ListViewType = (Seq[T], String => Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  type FormViewType = (Option[T], Form[T], Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  type DeleteViewType = (T, Call, Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  
  def builder: AccessibleEntity => T
  val form: Form[T]
  val listAction: Call
  val showAction: String => Call
  val createAction: Call
  val updateAction: String => Call
  val cancelAction: String => Call
  val deleteAction: String => Call
  val showView: ShowViewType
  val formView: FormViewType
  val listView: ListViewType
  val deleteView: DeleteViewType
  
  def list = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(entityType, maybeUser.flatMap(_.profile)).list.map { itemOrErr =>
          itemOrErr.right.map { lst => Ok(listView(lst.map(builder(_)), showAction, maybeUser, request)) }
        }
      }
  }

  def getJson(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item => Ok(generate(item.data)) }
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
                itemOrErr.right.map { item => Redirect(showAction(item.identifier)) }
              }
          }
        }
      )
  }

  def update(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item =>
            val doc: T = builder(item)
            Ok(formView(Some(doc), form.fill(doc), updateAction(id), maybeUser, request))
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
                  Redirect(showAction(item.identifier))
                }
              }
          }
        }
      )
  }

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
          boolOrErr.right.map { ok => Redirect(listAction) }
        }
      }
  }
}