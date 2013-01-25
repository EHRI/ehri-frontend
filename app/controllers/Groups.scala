package controllers

import play.api._
import play.api.i18n.Messages
import base._
import defines.{ ContentType, EntityType, PermissionType }
import play.api.libs.concurrent.Execution.Implicits._
import models.forms.{GroupF,VisibilityForm}
import models.{UserProfile, Group}
import models.base.Accessor
import scala.Some

object Groups extends PermissionHolderController[Group]
  with VisibilityController[Group]
  with CRUD[GroupF, Group]
  with PermissionItemController[Group]
  with AnnotationController[Group] {

  val entityType = EntityType.Group
  val contentType = ContentType.Group

  val form = models.forms.GroupForm.form
  val builder = Group

  def get(id: String) = getAction(id) { item => annotations =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.group.show(Group(item), annotations))
  }

  def list(page: Int = 1, limit: Int = DEFAULT_LIMIT) = listAction(page, limit) { page =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.group.list(page.copy(list = page.list.map(Group(_)))))
  }

  def create = createAction { users => groups => implicit user =>
    implicit request =>
      Ok(views.html.group.create(form, VisibilityForm.form, users, groups, routes.Groups.createPost))
  }

  def createPost = createPostAction(form) { formsOrItem =>
    implicit user =>
      implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getGroups(Some(user)) { users => groups =>
        BadRequest(views.html.group.create(errorForm, accForm, users, groups, routes.Groups.createPost))
      }
      case Right(item) => Redirect(routes.Groups.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.group.edit(
        Some(Group(item)), form.fill(Group(item).to), routes.Groups.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { item => formOrItem =>
    implicit user =>
      implicit request =>
        formOrItem match {
          case Left(errorForm) =>
            BadRequest(views.html.group.edit(Some(Group(item)), errorForm, routes.Groups.updatePost(id)))
          case Right(item) => Redirect(routes.Groups.get(item.id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
        }
  }

  def delete(id: String) = deleteAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.delete(
        Group(item), routes.Groups.deletePost(id),
        routes.Groups.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.Groups.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.permissions.visibility(Group(item),
        models.forms.VisibilityForm.form.fill(Group(item).accessors.map(_.id)),
        users, groups, routes.Groups.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.Groups.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def grantList(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = grantListAction(id, page, limit) {
    item => perms => implicit user => implicit request =>
      Ok(views.html.permissions.permissionGrantList(Group(item), perms))
  }

  def permissions(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = setGlobalPermissionsAction(id) {
    item => perms => implicit user => implicit request =>
      Ok(views.html.permissions.editGlobalPermissions(UserProfile(item), perms,
        routes.Groups.permissionsPost(id)))
  }

  def permissionsPost(id: String) = setGlobalPermissionsPostAction(id) { item => perms => implicit user =>
    implicit request =>
      Redirect(routes.Groups.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = manageItemPermissionsAction(id, page, limit) {
    item => perms => implicit user => implicit request =>
      Ok(views.html.permissions.managePermissions(UserProfile(item), perms,
        routes.Groups.addItemPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(Group(item), users, groups,
        routes.Groups.setItemPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionItem(Group(item), accessor, perms, contentType,
        routes.Groups.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
    bool => implicit user => implicit request =>
      Redirect(routes.Groups.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def annotate(id: String) = withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit user =>
    implicit request =>
      Ok(views.html.annotation.annotate(Group(item), models.forms.AnnotationForm.form, routes.Agents.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) { formOrAnnotation => implicit user =>
    implicit request =>
      formOrAnnotation match {
        case Left(errorForm) => getEntity(id, Some(user)) { item =>
          BadRequest(views.html.annotation.annotate(Group(item),
            errorForm, routes.Groups.annotatePost(id)))
        }
        case Right(annotation) => {
          Redirect(routes.Groups.get(id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
        }
      }
  }


  /*
   *	Membership
   */

  /**
   * Present a list of groups to which the current user can be added.
   */
  def membership(userType: String, userId: String) = withItemPermission(userId, PermissionType.Grant, ContentType.withName(userType)) { item => implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      Async {
        for {
          groups <- rest.RestHelpers.getGroupList
        } yield {
          // filter out the groups the user already belongs to
          val accessor = Accessor(item)
          val filteredGroups = groups.filter(t => t._1 != accessor.id).filter {
            case (ident, name) =>
              // if the user is admin, they can add the user to ANY group
              if (user.isAdmin) {
                !accessor.groups.map(_.id).contains(ident)
              } else {
                // if not, they can add the user to groups they belong to
                // TODO: Enforce these policies with the permission system!
                // TODO: WRITE TESTS FOR THESE WEIRD BEHAVIOURS!!!
                (!accessor.groups.map(_.id).contains(ident)) &&
                  user.groups.map(_.id).contains(ident)
              }
          }
          Ok(views.html.group.membership(accessor, filteredGroups))
        }
      }
  }

  /**
   * Confirm adding the given user to the specified group.
   */
  def addMember(id: String, userType: String, userId: String) = withItemPermission(userId, PermissionType.Grant, ContentType.withName(userType)) { item => implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        for {
          groupOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
        } yield {
          groupOrErr.right.map { group =>
            Ok(views.html.group.confirmMembership(builder(group), Accessor(item),
              routes.Groups.addMemberPost(id, userType, userId)))
          }
        }
      }
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def addMemberPost(id: String, userType: String, userId: String) = withItemPermission(userId, PermissionType.Grant, ContentType.withName(userType)) { item => implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        rest.PermissionDAO(user).addGroup(id, userId).map { boolOrErr =>
          boolOrErr.right.map { ok =>
            Redirect(routes.Groups.membership(userType, userId))
              .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
          }
        }
      }
  }

  /**
   * Confirm adding the given user to the specified group.
   */
  def removeMember(id: String, userType: String, userId: String) = withItemPermission(userId, PermissionType.Grant, ContentType.withName(userType)) { item => implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        for {
          groupOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
        } yield {
          groupOrErr.right.map { group =>
            Ok(views.html.group.removeMembership(builder(group), Accessor(item),
              routes.Groups.removeMemberPost(id, userType, userId)))
          }
        }
      }
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def removeMemberPost(id: String, userType: String, userId: String) = withItemPermission(userId, PermissionType.Grant, ContentType.withName(userType)) { item => implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        rest.PermissionDAO(user).removeGroup(id, userId).map { boolOrErr =>
          boolOrErr.right.map { ok =>
            Redirect(routes.Groups.membership(userType, userId))
              .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
          }
        }
      }
  }
}
