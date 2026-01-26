package controllers.groups

import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import forms._
import models.{Accessor, EntityType, Group, UserProfile}
import play.api.data.Forms.{nonEmptyText, single}
import play.api.data.{Form, Forms}
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request}
import services.data.{Constants, DataHelpers}
import services.search._
import utils.{PageParams, RangeParams}

import javax.inject._
import scala.concurrent.Future

case class Groups @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers
) extends AdminController
  with PermissionHolder[Group]
  with Visibility[Group]
  with Membership[Group]
  with ItemPermissions[Group]
  with CRUD[Group]
  with SearchType[Group] {

  private val form = models.Group.form
  private val groupRoutes = controllers.groups.routes.Groups

  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key="active",
        name=Messages("group.active"),
        param="active",
        render=s => Messages("group.active." + s),
        facets=List(
          QueryFacet(value = "true", range = Val("1")),
          QueryFacet(value = "false", range = Val("0"))
        ),
        display = FacetDisplay.Boolean
      )
    )
  }

  def get(id: String, paging: PageParams): Action[AnyContent] = ItemMetaAction(id).async { implicit request =>
    for {
      page <- userDataApi.children[Group, Accessor](id, paging)
      accs <- accounts.findAllById(ids = page.items.collect { case up: UserProfile => up.id })
    } yield {
      val pageWithAccounts = page.copy(items = page.items.map {
        case up: UserProfile => up.copy(account = accs.find(_.id == up.id))
        case group => group
      })
      Ok(views.html.admin.group.show(request.item, pageWithAccounts, paging, request.annotations))
    }
  }

  def history(id: String, range: RangeParams): Action[AnyContent] = ItemHistoryAction(id, range).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def search(params: SearchParams, paging: PageParams): Action[AnyContent] =
    SearchTypeAction(params, paging, facetBuilder = entityFacets).apply { implicit request =>
      Ok(views.html.admin.group.search(request.result, groupRoutes.search()))
    }

  def create: Action[AnyContent] = NewItemAction.apply { implicit request =>
    Ok(views.html.admin.group.create(form, visibilityForm,
      request.usersAndGroups, groupRoutes.createPost()))
  }

  /**
   * Extract a set of group members from the form POST data and
   * convert it into params for the dataApi call.
   */
  private def memberExtractor: Request[_] => Map[String,Seq[String]] = { implicit r =>
    Map(Constants.MEMBER -> Form(Forms.single(
      Constants.MEMBER -> Forms.seq(Forms.nonEmptyText)
    )).bindFromRequest().value.getOrElse(Seq.empty))
  }

  def createPost: Action[AnyContent] = CreateItemAction(form, memberExtractor).apply { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm, _, usersAndGroups)) =>
        BadRequest(views.html.admin.group.create(
          errorForm, accForm, usersAndGroups, groupRoutes.createPost()))
      case Right(item) => Redirect(groupRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.group.edit(
        request.item, form.fill(request.item.data), groupRoutes.updatePost(id)))
  }

  def updatePost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.group.edit(
          request.item, errorForm, groupRoutes.updatePost(id)))
      case Right(updated) => Redirect(groupRoutes.get(updated.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def delete(id: String): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, groupRoutes.deletePost(id), groupRoutes.get(id)))
  }

  def deletePost(id: String): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(groupRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def grantList(id: String, paging: PageParams): Action[AnyContent] = GrantListAction(id, paging).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionGrantList(request.item, request.permissionGrants))
  }

  def managePermissions(id: String, paging: PageParams): Action[AnyContent] =
    PermissionGrantAction(id, paging).apply { implicit request =>
      Ok(views.html.admin.permissions.managePermissions(request.item, request.permissionGrants,
        groupRoutes.addItemPermissions(id)))
    }

  def addItemPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.usersAndGroups,
      groupRoutes.setItemPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
          groupRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(groupRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def permissions(id: String): Action[AnyContent] =
    CheckGlobalPermissionsAction(id).apply { implicit request =>
      Ok(views.html.admin.permissions.editGlobalPermissions(request.item, request.permissions,
            groupRoutes.permissionsPost(id)))
    }

  def permissionsPost(id: String): Action[AnyContent] = SetGlobalPermissionsAction(id).apply { implicit request =>
    Redirect(groupRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def revokePermission(id: String, permId: String): Action[AnyContent] = {
    CheckRevokePermissionAction(id, permId).apply { implicit request =>
      Ok(views.html.admin.permissions.revokePermission(
        request.item, request.permissionGrant,
        groupRoutes.revokePermissionPost(id, permId), groupRoutes.grantList(id)))
    }
  }

  def revokePermissionPost(id: String, permId: String): Action[AnyContent] = {
    RevokePermissionAction(id, permId).apply { implicit request =>
      Redirect(groupRoutes.grantList(id))
        .flashing("success" -> "item.delete.confirmation")
    }
  }

  def membership(id: String): Action[AnyContent] = MembershipAction(id).apply { implicit request =>
    Ok(views.html.admin.group.membership(request.item, request.groups))
  }

  def checkAddToGroup(id: String, groupId: String): Action[AnyContent] =
    CheckManageGroupAction(id, groupId).apply { implicit request =>
      Ok(views.html.admin.group.confirmMembership(request.group, request.item,
        groupRoutes.addToGroup(id, groupId)))
    }

  def addToGroup(id: String, groupId: String): Action[AnyContent] =
    AddToGroupAction(id, groupId).apply { implicit request =>
      Redirect(groupRoutes.membership(id))
        .flashing("success" -> "item.update.confirmation")
    }

  def checkRemoveFromGroup(id: String, groupId: String): Action[AnyContent] =
    CheckManageGroupAction(id, groupId).apply { implicit request =>
      Ok(views.html.admin.group.removeMembership(request.group, request.item,
                groupRoutes.removeFromGroup(id, groupId)))
    }

  def removeFromGroup(id: String, groupId: String): Action[AnyContent] =
    RemoveFromGroupAction(id, groupId).apply { implicit request =>
      Redirect(groupRoutes.membership(id))
                .flashing("success" -> "item.update.confirmation")
    }

  private val membersForm: Form[Set[String]] = Form(single(
    services.data.Constants.MEMBER -> Forms.set(nonEmptyText)
  ))

  def members(id: String): Action[AnyContent] = MembersAction(id).apply { implicit request =>
      Ok(views.html.admin.group.members(request.item, request.currentMembers,
        request.usersAndGroups, groupRoutes.membersPost(id)))
  }

  def membersPost(id: String): Action[AnyContent] = MembersAction(id).async { implicit request =>
    membersForm.bindFromRequest().fold(
      err => Future.successful(BadRequest(
        views.html.admin.group.members(request.item, err.value.getOrElse(Set.empty),
          request.usersAndGroups, groupRoutes.membersPost(id)))),

      data => userDataApi.setGroupMembers(id, data).map { _ =>
        Redirect(groupRoutes.get(id)).flashing("success" -> "item.update.confirmation")
      }
    )
  }
}
