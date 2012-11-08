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
import defines._
import play.api.mvc.Call
import models._

object DocumentaryUnits extends DocumentaryUnitContext[DocumentaryUnit] with EntityRead[DocumentaryUnit] with EntityUpdate[DocumentaryUnit] with EntityDelete[DocumentaryUnit] {
  val entityType = EntityType.DocumentaryUnit
  val listAction = routes.DocumentaryUnits.list _
  val cancelAction = routes.DocumentaryUnits.get _
  val deleteAction = routes.DocumentaryUnits.deletePost _
  val updateAction = routes.DocumentaryUnits.updatePost _
  val form = forms.DocumentaryUnitForm.form
  val docForm = forms.DocumentaryUnitForm.form
  val showAction = routes.DocumentaryUnits.get _
  val docShowAction = routes.DocumentaryUnits.get _
  val docCreateAction = routes.DocumentaryUnits.docCreatePost _
  val formView = views.html.documentaryUnit.edit.apply _
  val showView = views.html.documentaryUnit.show.apply _
  val listView = views.html.documentaryUnit.list.apply _
  val docFormView = views.html.documentaryUnit.create.apply _  
  val deleteView = views.html.delete.apply _
  val builder: (AccessibleEntity => DocumentaryUnit) = DocumentaryUnit.apply _
}

object UserProfiles extends AccessorController[UserProfile] with CRUD[UserProfile] {
  val entityType = EntityType.UserProfile
  val listAction = routes.UserProfiles.list _
  val createAction = routes.UserProfiles.createPost
  val updateAction = routes.UserProfiles.updatePost _
  val cancelAction = routes.UserProfiles.get _
  val deleteAction = routes.UserProfiles.deletePost _
  val permsAction = routes.UserProfiles.permissions _
  val setPermsAction = routes.UserProfiles.permissionsPost _
  val form = forms.UserProfileForm.form
  val showAction = routes.UserProfiles.get _
  val formView = views.html.userProfile.edit.apply _
  val showView = views.html.userProfile.show.apply _
  val listView = views.html.list.apply _
  val deleteView = views.html.delete.apply _
  val permView = views.html.permissions.edit.apply _
  val builder: (AccessibleEntity => UserProfile) = UserProfile.apply _
}

object Groups extends AccessorController[Group] with CRUD[Group] {
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
  val builder: (AccessibleEntity => Group) = Group.apply _
}

object Agents extends DocumentaryUnitContext[Agent] with CRUD[Agent] {
  val entityType = EntityType.Agent
  val listAction = routes.Agents.list _
  val createAction = routes.Agents.createPost
  val updateAction = routes.Agents.updatePost _
  val cancelAction = routes.Agents.get _
  val deleteAction = routes.Agents.deletePost _
  val docShowAction = routes.DocumentaryUnits.get _
  val docCreateAction = routes.Agents.docCreatePost _
  val form = forms.AgentForm.form
  val docForm = forms.DocumentaryUnitForm.form
  val showAction = routes.Agents.get _
  val formView = views.html.agent.edit.apply _
  val showView = views.html.agent.show.apply _
  val showDocView = views.html.documentaryUnit.show.apply _
  val docFormView = views.html.documentaryUnit.create.apply _
  val listView = views.html.list.apply _
  val deleteView = views.html.delete.apply _
  val builder: (AccessibleEntity => Agent) = Agent.apply _
}

trait CRUD[T <: ManagedEntity] extends EntityCreate[T] with EntityRead[T] with EntityUpdate[T] with EntityDelete[T]

trait DocumentaryUnitContext[T <: ManagedEntity] extends EntityController[T] {
  
  type DocFormViewType = (T, Form[DocumentaryUnit], 
		  Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val docFormView: DocFormViewType
  val docForm: Form[DocumentaryUnit]
  val docShowAction: String => Call	
  val docCreateAction: String => Call
  
  def docCreate(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item => Ok(docFormView(builder(item), docForm, docCreateAction(id), maybeUser, request)) }
        }
      }            
  }

  def docCreatePost(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      docForm.bindFromRequest.fold(
        errorForm => {
          AsyncRest {
            EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
              itemOrErr.right.map { item =>
                BadRequest(docFormView(builder(item), errorForm, docCreateAction(id), maybeUser, request))
              }
            }
          }
        },
        doc => {
          AsyncRest {
            EntityDAO(entityType, maybeUser.flatMap(_.profile))
              .createInContext(EntityType.DocumentaryUnit, id, doc.toData).map { itemOrErr =>
                itemOrErr.right.map { item =>
                  Redirect(docShowAction(item.identifier))
                }
              }
          }
        }
      )      
  }  
}

trait AccessorController[T <: Accessor] extends EntityController[T] {

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
            permsOrErr <- PermissionDAO(userProfile).get(builder(itemOrErr.right.get))
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

trait EntityController[T <: ManagedEntity] extends Controller with AuthController with ControllerHelpers {
  val entityType: EntityType.Value
  def builder: AccessibleEntity => T
}

trait EntityRead[T <: ManagedEntity] extends EntityController[T] {

  type ShowViewType = (T, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  type ListViewType = (Seq[T], String => Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val listView: ListViewType
  val listAction: (Int, Int) => Call
  val showAction: String => Call
  val showView: ShowViewType

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
  
  def list(offset: Int, limit: Int) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(entityType, maybeUser.flatMap(_.profile))
          .list(math.max(offset, 0), math.max(limit, 20)).map { itemOrErr =>
            itemOrErr.right.map { lst => Ok(listView(lst.map(builder(_)), showAction, maybeUser, request)) }
          }
      }
  }  
}

trait EntityCreate[T <: ManagedEntity] extends EntityRead[T] {
  type FormViewType = (Option[T], Form[T], Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val createAction: Call
  val formView: FormViewType
  val form: Form[T]
  
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
}

trait EntityUpdate[T <: ManagedEntity] extends EntityRead[T] {
  val updateAction: String => Call
  val formView: EntityCreate[T]#FormViewType
  val form: Form[T]
  
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
}
  
trait EntityDelete[T <: ManagedEntity] extends EntityRead[T] {

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