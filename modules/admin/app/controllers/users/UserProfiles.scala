package controllers.users

import acl.ItemPermissionSet
import auth.{HashedPassword, AccountManager}
import play.api.libs.concurrent.Execution.Implicits._
import controllers.generic._
import models._
import play.api.i18n.Messages
import defines.{EntityType, PermissionType, ContentTypes}
import utils.search._
import com.google.inject._
import backend.Backend
import play.api.data.{FormError, Forms, Form}
import scala.concurrent.Future.{successful => immediate}
import play.api.libs.json.Json
import scala.concurrent.{Await, Future}
import play.api.mvc.Request
import java.util.concurrent.TimeUnit
import backend.rest.{ValidationError, RestHelpers}
import scala.concurrent.duration.Duration
import play.api.mvc.Result
import play.api.libs.json.JsObject
import controllers.base.AdminController


@Singleton
case class UserProfiles @Inject()(implicit globalConfig: global.GlobalConfig, searchIndexer: Indexer, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountManager)
  extends AdminController
  with PermissionHolder[UserProfile]
  with ItemPermissions[UserProfile]
  with Read[UserProfile]
  with Update[UserProfileF,UserProfile]
  with Delete[UserProfile]
  with Membership[UserProfile]
  with Search {

  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key="active",
        name=Messages("userProfile.active"),
        param="active",
        render=s => Messages("userProfile.active." + s),
        facets=List(
          QueryFacet(value = "true", range = Val("1")),
          QueryFacet(value = "false", range = Val("0"))
        ),
        display = FacetDisplay.Boolean
      ),
      FieldFacetClass(
        key="groupName",
        name=Messages("contentTypes.group"),
        param="group",
        sort = FacetSort.Name
      )
    )
  }

  val form = models.UserProfile.form

  private val userRoutes = controllers.users.routes.UserProfiles


  private val groupMembershipForm = Form(Forms.single("group" -> Forms.list(Forms.nonEmptyText)))

  private val userPasswordForm = Form(
    Forms.tuple(
      "email" -> Forms.email,
      "identifier" -> Forms.nonEmptyText(minLength= 3, maxLength = 20),
      "name" -> Forms.nonEmptyText,
      "password" -> Forms.nonEmptyText(minLength = 6),
      "confirm" -> Forms.nonEmptyText(minLength = 6)
    ) verifying("login.error.passwordsDoNotMatch", f => f match {
      case (_, _, _, pw, pwc) => pw == pwc
    })
  )

  /**
   * Create a user's account for them with a pre-set password. This is an
   * admin only function and should be removed eventually.
   */
  def createUser = WithContentPermissionAction(PermissionType.Create, ContentTypes.UserProfile).async { implicit request =>
      getGroups { groups =>
        Ok(views.html.admin.userProfile.create(userPasswordForm, groupMembershipForm, groups,
          userRoutes.createUserPost()))
      }
  }

  /**
   * Create a user. Currently this gets a bit gnarly. I'd like
   * to apologise to the world for the state of this code.
   *
   * We basically have to nit together a bunch of Rest operations
   * with an account db operation, and handle various different
   * types of validation:
   *
   *  - bind the form, if it's okay manually construct a user object
   *  - try and save the user object - if server validation fails,
   *    i.e. username already exists, redisplay the form with the
   *    appropriate error.
   *  - we also need a list of all possible groups the user
   *    could be added to
   *  - we also need to tweak permissions on the user's own
   *    account so they can edit it... all in all not nice.
   *
   * @return
   */
  def createUserPost = WithContentPermissionAction(PermissionType.Create, ContentTypes.UserProfile).async { implicit request =>

    // Blocking! This helps simplify the nest of callbacks.
      val allGroups: Seq[(String, String)] = Await.result(
        RestHelpers.getGroupList, Duration(1, TimeUnit.MINUTES))

      userPasswordForm.bindFromRequest.fold(
      errorForm => {
        immediate(Ok(views.html.admin.userProfile.create(errorForm, groupMembershipForm.bindFromRequest,
          allGroups, userRoutes.createUserPost())))
      },
      {
        case (em, username, name, pw, _) =>
          saveUser(em, username, name, pw, allGroups)
      }
      )
  }

  /**
   * Create a user's profile on the ReSt interface.
   */
  private def createUserProfile[T](user: UserProfileF, groups: Seq[String], allGroups: Seq[(String,String)])(
    implicit request: Request[T], userOpt: Option[UserProfile]): Future[Either[ValidationError,UserProfile]] = {
    backend.create[UserProfile,UserProfileF](user, params = Map("group" -> groups)).map { item =>
      Right(item)
    } recover {
      case e@ValidationError(errorSet) => Left(e)
    }
  }

  /**
   * Save a user, creating both an account and a profile.
   */
  private def saveUser[T](email: String, username: String, name: String, pw: String, allGroups: Seq[(String, String)])(
    implicit request: Request[T], userOpt: Option[UserProfile]): Future[Result] = {
    // check if the email is already registered...
    userDAO.findByEmail(email.toLowerCase).flatMap {
      case Some(account) =>
        val errForm = userPasswordForm.bindFromRequest
          .withError(FormError("email", Messages("error.userEmailAlreadyRegistered", account.id)))
        immediate(BadRequest(views.html.admin.userProfile.create(errForm, groupMembershipForm.bindFromRequest,
          allGroups, userRoutes.createUserPost())))
      case None =>
        // It's not registered, so create the account...
        val user = UserProfileF(id = None, identifier = username, name = name,
          location = None, about = None, languages = Nil)
        val groups = groupMembershipForm.bindFromRequest.value.getOrElse(List())
        createUserProfile(user, groups, allGroups).flatMap {
          case Left(ValidationError(errorSet)) =>
            val errForm = user.getFormErrors(errorSet, userPasswordForm.bindFromRequest)
            immediate(BadRequest(views.html.admin.userProfile.create(errForm, groupMembershipForm.bindFromRequest,
              allGroups, userRoutes.createUserPost())))
          case Right(profile) => for {
            account <- userDAO.create(Account(
              id = profile.id,
              email = email.toLowerCase,
              verified = true,
              active = true,
              staff = true,
              allowMessaging = true,
              password = Some(HashedPassword.fromPlain(pw))
            ))
            _ <- backend.setItemPermissions(profile.id, ContentTypes.UserProfile,
              profile.id, List(PermissionType.Owner.toString))
          } yield Redirect(controllers.users.routes.UserProfiles.search())
        }
    }
  }

  def get(id: String) = ItemMetaAction(id).apply {  implicit request =>
    Ok(views.html.admin.userProfile.show(request.item, request.annotations))
  }

  def search = OptionalUserAction.async { implicit request =>
    for {
      QueryResult(page, params, facets) <- find[UserProfile](
        entities = List(EntityType.UserProfile), facetBuilder = entityFacets)
      accounts <- userDAO.findAllById(ids = page.items.map(_._1.id))
    } yield {
      val pageWithAccounts = page.copy(items = page.items.map { case(up, hit) =>
        up.copy(account = accounts.find(_.id == up.id)) -> hit
      })
      Ok(views.html.admin.userProfile.search(pageWithAccounts, params, facets, userRoutes.search()))
    }
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvents.itemList(request.item, request.page, request.params))
  }

  def list = ItemPageAction.apply { implicit request =>
    Ok(views.html.admin.userProfile.list(request.page, request.params))
  }

  def update(id: String) = WithItemPermissionAction(id, PermissionType.Update).async { implicit request =>
    userDAO.findById(request.item.id).map { accountOpt =>
      val userWithAccount = request.item.copy(account = accountOpt)
      Ok(views.html.admin.userProfile.edit(request.item, AdminUserData.form.fill(
        AdminUserData.fromUserProfile(userWithAccount)),
        userRoutes.updatePost(id)))
    }
  }

  def updatePost(id: String) = WithItemPermissionAction(id, PermissionType.Update).async { implicit request =>
    userDAO.findById(request.item.id).flatMap { accountOpt =>
      val userWithAccount = request.item.copy(account = accountOpt)
      AdminUserData.form.bindFromRequest.fold(
        errForm => immediate(BadRequest(views.html.admin.userProfile.edit(userWithAccount, errForm,
          userRoutes.updatePost(id)))),
        data => accountOpt match {
          case Some(account) => for {
            profile <- backend.patch[UserProfile](id, Json.toJson(data).as[JsObject])
            newAccount <- userDAO.update(account.copy(active = data.active, staff = data.staff))
          } yield Redirect(userRoutes.search())
              .flashing("success" -> Messages("item.update.confirmation", request.item.toStringLang))
          case None => for {
            profile <- backend.patch[UserProfile](id, Json.toJson(data).as[JsObject])
          } yield Redirect(userRoutes.search())
              .flashing("success" -> Messages("item.update.confirmation", request.item.toStringLang))
        }
      )
    }
  }

  private def deleteForm(user: UserProfile): Form[String] = Form(
    Forms.single("deleteCheck" -> Forms.nonEmptyText
      .verifying("error.invalidName", name => name == user.model.name))
  )

  def delete(id: String) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.userProfile.delete(
      request.item, deleteForm(request.item), userRoutes.deletePost(id),
          userRoutes.get(id)))
  }

  def deletePost(id: String) = WithItemPermissionAction(id, PermissionType.Delete).async { implicit request =>
    deleteForm(request.item).bindFromRequest.fold(
      errForm => {
        immediate(BadRequest(views.html.admin.userProfile.delete(
          request.item, deleteForm(request.item), userRoutes.deletePost(id),
          userRoutes.get(id))))
      },
      _ => {
        userDAO.findById(id).flatMap {
          case Some(account) => for {
            _ <- backend.delete[UserProfile](id, logMsg = getLogMessage)
            _ <- userDAO.delete(account)
          } yield Redirect(userRoutes.search())
              .flashing("success" -> Messages("item.delete.confirmation", id))
          case None => for {
            _ <- backend.delete[UserProfile](id, logMsg = getLogMessage)
          } yield Redirect(userRoutes.search())
              .flashing("success" -> Messages("item.delete.confirmation", id))
        }
      }
    )
  }

  def grantList(id: String) = GrantListAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionGrantList(
      request.item, request.permissionGrants))
  }

  def permissions(id: String) = CheckGlobalPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.editGlobalPermissions(
      request.item, request.permissions,
        userRoutes.permissionsPost(id)))
  }

  def permissionsPost(id: String) = SetGlobalPermissionsAction(id).apply { implicit request =>
    Redirect(userRoutes.get(id))
        .flashing("success" -> Messages("item.update.confirmation", id))
  }

  def revokePermission(id: String, permId: String) = {
    CheckRevokePermissionAction(id, permId).apply { implicit request =>
      Ok(views.html.admin.permissions.revokePermission(
        request.item, request.permissionGrant,
        userRoutes.revokePermissionPost(id, permId), userRoutes.grantList(id)))
    }
  }

  def revokePermissionPost(id: String, permId: String) = {
    RevokePermissionAction(id, permId).apply { implicit request =>
      Redirect(userRoutes.grantList(id))
        .flashing("success" -> Messages("item.delete.confirmation", id))
    }
  }

  def managePermissions(id: String) = PermissionGrantAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.managePermissions(request.item, request.permissionGrants,
        userRoutes.addItemPermissions(id)))
  }

  def addItemPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.users, request.groups,
        userRoutes.setItemPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions, UserProfile.Resource.contentType,
        userRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(userRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def membership(id: String) = MembershipAction(id).apply { implicit request =>
    Ok(views.html.admin.group.membership(request.item, request.groups))
  }

  def checkAddToGroup(id: String, groupId: String) = CheckManageGroupAction(id, groupId).apply { implicit request =>
    Ok(views.html.admin.group.confirmMembership(request.group, request.item,
      userRoutes.addToGroup(id, groupId)))
  }

  def addToGroup(id: String, groupId: String) = AddToGroupAction(id, groupId).apply { implicit request =>
    Redirect(userRoutes.membership(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def checkRemoveFromGroup(id: String, groupId: String) = CheckManageGroupAction(id, groupId).apply { implicit request =>
    Ok(views.html.admin.group.removeMembership(request.group, request.item,
      userRoutes.removeFromGroup(id, groupId)))
  }

  def removeFromGroup(id: String, groupId: String) = RemoveFromGroupAction(id, groupId).apply { implicit request =>
    Redirect(userRoutes.membership(id))
      .flashing("success" -> "item.update.confirmation")
  }
}

