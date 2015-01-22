package controllers.groups

import auth.AccountManager
import play.api.libs.concurrent.Execution.Implicits._
import controllers.generic._
import forms.VisibilityForm
import models.{UserProfile, Group, GroupF}
import models.base.Accessor
import com.google.inject._
import utils.PageParams
import utils.search.{Resolver, Dispatcher}
import scala.concurrent.Future
import backend.Backend
import backend.rest.Constants
import play.api.mvc.Request
import play.api.data.{Forms, Form}
import controllers.base.AdminController

case class Groups @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, accounts: AccountManager) extends AdminController
  with PermissionHolder[Group]
  with Visibility[Group]
  with Membership[Group]
  with CRUD[GroupF, Group] {

  private val form = models.Group.form
  private val groupRoutes = controllers.groups.routes.Groups

  def get(id: String) = ItemMetaAction(id).async { implicit request =>
    val params = PageParams.fromRequest(request)
    for {
      page <- backend.listChildren[Group,Accessor](id, params)
      accs <- accounts.findAllById(ids = page.items.collect { case up: UserProfile => up.id })
    } yield {
      val pageWithAccounts = page.copy(items = page.items.map {
        case up: UserProfile => up.copy(account = accs.find(_.id == up.id))
        case group => group
      })
      Ok(views.html.admin.group.show(request.item, pageWithAccounts, params, request.annotations))
    }
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvents.itemList(request.item, request.page, request.params))
  }

  def list = ItemPageAction.apply { implicit request =>
    Ok(views.html.admin.group.list(request.page, request.params))
  }

  def create = NewItemAction.apply { implicit request =>
    Ok(views.html.admin.group.create(form, VisibilityForm.form,
      request.users, request.groups, groupRoutes.createPost()))
  }

  /**
   * Extract a set of group members from the form POST data and
   * convert it into params for the backend call.
   */
  private def memberExtractor: Request[_] => Map[String,Seq[String]] = { implicit r =>
    Map(Constants.MEMBER -> Form(Forms.single(
      Constants.MEMBER -> Forms.seq(Forms.nonEmptyText)
    )).bindFromRequest().value.getOrElse(Seq.empty))
  }

  def createPost = CreateItemAction(form, memberExtractor).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.admin.group.create(
          errorForm, accForm, users, groups, groupRoutes.createPost()))
      }
      case Right(item) => Future.successful(Redirect(groupRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def update(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.group.edit(
        request.item, form.fill(request.item.model), groupRoutes.updatePost(id)))
  }

  def updatePost(id: String) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.group.edit(
          request.item, errorForm, groupRoutes.updatePost(id)))
      case Right(updated) => Redirect(groupRoutes.get(updated.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def delete(id: String) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, groupRoutes.deletePost(id), groupRoutes.get(id)))
  }

  def deletePost(id: String) = DeleteAction(id).apply { implicit request =>
    Redirect(groupRoutes.list())
        .flashing("success" -> "item.delete.confirmation")
  }

  def grantList(id: String) = GrantListAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionGrantList(request.item, request.permissionGrants))
  }

  def permissions(id: String) = CheckGlobalPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.editGlobalPermissions(request.item, request.permissions,
          groupRoutes.permissionsPost(id)))
  }

  def permissionsPost(id: String) = SetGlobalPermissionsAction(id).apply { implicit request =>
    Redirect(groupRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }


  def revokePermission(id: String, permId: String) = {
    CheckRevokePermissionAction(id, permId).apply { implicit request =>
      Ok(views.html.admin.permissions.revokePermission(
        request.item, request.permissionGrant,
        groupRoutes.revokePermissionPost(id, permId), groupRoutes.grantList(id)))
    }
  }

  def revokePermissionPost(id: String, permId: String) = {
    RevokePermissionAction(id, permId).apply { implicit request =>
      Redirect(groupRoutes.grantList(id))
        .flashing("success" -> "item.delete.confirmation")
    }
  }

  def membership(id: String) = MembershipAction(id).apply { implicit request =>
    Ok(views.html.admin.group.membership(request.item, request.groups))
  }

  def checkAddToGroup(id: String, groupId: String) = CheckManageGroupAction(id, groupId).apply { implicit request =>
    Ok(views.html.admin.group.confirmMembership(request.group, request.item,
      groupRoutes.addToGroup(id, groupId)))
  }

  def addToGroup(id: String, groupId: String) = AddToGroupAction(id, groupId).apply { implicit request =>
    Redirect(groupRoutes.membership(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def checkRemoveFromGroup(id: String, groupId: String) = CheckManageGroupAction(id, groupId).apply { implicit request =>
    Ok(views.html.admin.group.removeMembership(request.group, request.item,
              groupRoutes.removeFromGroup(id, groupId)))
  }

  def removeFromGroup(id: String, groupId: String) = RemoveFromGroupAction(id, groupId).apply { implicit request =>
    Redirect(groupRoutes.membership(id))
              .flashing("success" -> "item.update.confirmation")
  }
}
