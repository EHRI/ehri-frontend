package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import controllers.generic._
import forms.VisibilityForm
import models.{UserProfile, AccountDAO, Group, GroupF}
import models.base.Accessor
import play.api.i18n.Messages
import defines.{EntityType, ContentTypes, PermissionType}
import com.google.inject._
import utils.search.{Resolver, Dispatcher}
import scala.concurrent.Future
import backend.Backend
import backend.rest.{Constants, RestHelpers}
import models.json.RestResource
import play.api.mvc.{Request, AnyContent}
import play.api.data.{Forms, Form}

case class Groups @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends PermissionHolder[Group]
  with Visibility[Group]
  with CRUD[GroupF, Group] {

  val contentType = ContentTypes.Group
  implicit val resource = Group.Resource

  private val form = models.Group.form
  private val groupRoutes = controllers.admin.routes.Groups

  def get(id: String) = getWithChildrenAction[Accessor](id) {
      item => page => params => annotations => links => implicit maybeUser => implicit request =>
    val pageWithAccounts = page.copy(items = page.items.map {
      case up: UserProfile => up.copy(account = userDAO.findByProfileId(up.id))
      case group => group
    })
    Ok(views.html.group.show(item, pageWithAccounts, params, annotations))
  }

  def history(id: String) = historyAction(id) {
      item => page => params => implicit userOptOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit maybeUser => implicit request =>
    Ok(views.html.group.list(page, params))
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.group.create(form, VisibilityForm.form,
        users, groups, groupRoutes.createPost()))
  }

  /**
   * Extract a set of group members from the form POST data and
   * convert it into params for the backend call.
   */
  private def memberExtractor: Request[AnyContent] => Map[String,Seq[String]] = { implicit r =>
    Map(Constants.MEMBER -> Form(Forms.single(
      Constants.MEMBER -> Forms.seq(Forms.nonEmptyText)
    )).bindFromRequest().value.getOrElse(Seq.empty))
  }

  def createPost = createPostAction.async(form, memberExtractor) {
      formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.group.create(
          errorForm, accForm, users, groups, groupRoutes.createPost()))
      }
      case Right(item) => Future.successful(Redirect(groupRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.group.edit(
        item, form.fill(item.model), groupRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.group.edit(item, errorForm, groupRoutes.updatePost(id)))
      case Right(updated) => Redirect(groupRoutes.get(updated.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, groupRoutes.deletePost(id), groupRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(groupRoutes.list())
        .flashing("success" -> "item.delete.confirmation")
  }

  def grantList(id: String) = grantListAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionGrantList(item, perms))
  }

  def permissions(id: String) = setGlobalPermissionsAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.editGlobalPermissions(item, perms,
          groupRoutes.permissionsPost(id)))
  }

  def permissionsPost(id: String) = setGlobalPermissionsPostAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Redirect(groupRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }


  def revokePermission(id: String, permId: String) = revokePermissionAction(id, permId) {
      item => perm => implicit userOpt => implicit request =>
    Ok(views.html.permissions.revokePermission(item, perm,
        groupRoutes.revokePermissionPost(id, permId), groupRoutes.grantList(id)))
  }

  def revokePermissionPost(id: String, permId: String) = revokePermissionActionPost(id, permId) {
      item => bool => implicit userOpt => implicit request =>
    Redirect(groupRoutes.grantList(id))
      .flashing("success" -> "item.delete.confirmation")
  }

  /*
   *	Membership
   */

  /**
   * Present a list of groups to which the current user can be added.
   */
  def membership(userType: EntityType.Value, userId: String) = {
    implicit val resource = Accessor.resourceFor(userType)
    withItemPermission.async[Accessor](userId, PermissionType.Grant, ContentTypes.withName(userType)) {
        item => implicit userOpt => implicit request =>
      for {
        groups <- RestHelpers.getGroupList
      } yield {
        // filter out the groups the user already belongs to
        val filteredGroups = groups.filter(t => t._1 != item.id).filter {
          case (ident, name) =>
            // if the user is admin, they can add the user to ANY group
            if (userOpt.get.isAdmin) {
              !item.groups.map(_.id).contains(ident)
            } else {
              // if not, they can add the user to groups they belong to
              // TODO: Enforce these policies with the permission system!
              // TODO: WRITE TESTS FOR THESE WEIRD BEHAVIOURS!!!
              (!item.groups.map(_.id).contains(ident)) &&
                userOpt.get.groups.map(_.id).contains(ident)
            }
        }
        Ok(views.html.group.membership(item, filteredGroups))
      }
    }
  }

  /**
   * Confirm adding the given user to the specified group.
   */
  def addMember(id: String, userType: EntityType.Value, userId: String) = {
    implicit val resource = Accessor.resourceFor(userType)
    withItemPermission.async[Accessor](userId, PermissionType.Grant, ContentTypes.withName(userType)) {
        item => implicit userOpt => implicit request =>
      backend.get[Group](id).map { group =>
        Ok(views.html.group.confirmMembership(group, item,
          groupRoutes.addMemberPost(id, userType, userId)))
      }
    }
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def addMemberPost(id: String, userType: EntityType.Value, userId: String) = {
    implicit val resource = Accessor.resourceFor(userType)
    withItemPermission.async[Accessor](userId, PermissionType.Grant, ContentTypes.withName(userType)) {
        item => implicit userOpt => implicit request =>
      backend.addGroup(id, userId).map { ok =>
        Redirect(groupRoutes.membership(userType, userId))
          .flashing("success" -> "item.update.confirmation")
      }
    }
  }

  /**
   * Confirm adding the given user to the specified group.
   */
  def removeMember(id: String, userType: EntityType.Value, userId: String) = {
    implicit val resource = Accessor.resourceFor(userType)
    withItemPermission.async[Accessor](userId, PermissionType.Grant, ContentTypes.withName(userType)) {
        item => implicit userOpt => implicit request =>
      backend.get[Group](id).map { group =>
        Ok(views.html.group.removeMembership(group, item,
          groupRoutes.removeMemberPost(id, userType, userId)))
      }
    }
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def removeMemberPost(id: String, userType: EntityType.Value, userId: String) = {
    implicit val resource = Accessor.resourceFor(userType)
    withItemPermission.async[Accessor](userId, PermissionType.Grant, ContentTypes.withName(userType)) {
        item => implicit userOpt => implicit request =>
      backend.removeGroup(id, userId).map { ok =>
        Redirect(groupRoutes.membership(userType, userId))
          .flashing("success" -> "item.update.confirmation")
      }
    }
  }
}
