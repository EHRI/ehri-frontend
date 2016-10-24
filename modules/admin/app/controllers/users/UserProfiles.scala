package controllers.users

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import auth.{AccountManager, HashedPassword}
import controllers.core.auth.AccountHelpers
import play.api.cache.CacheApi
import play.api.http.HeaderNames
import play.api.libs.concurrent.Execution.Implicits._
import controllers.generic._
import models._
import play.api.i18n.{Messages, MessagesApi}
import defines.{ContentTypes, EntityType, PermissionType}
import utils.{CsvHelpers, MovedPageLookup, PageParams}
import utils.search._
import javax.inject._

import backend.DataApi
import play.api.data.{Form, FormError, Forms}
import views.MarkdownRenderer

import scala.concurrent.Future.{successful => immediate}
import play.api.libs.json.Json

import scala.concurrent.Future
import play.api.mvc.Request
import backend.rest.{DataHelpers, ValidationError}
import play.api.mvc.Result
import play.api.libs.json.JsObject
import controllers.base.AdminController


@Singleton
case class UserProfiles @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchIndexer: SearchIndexMediator,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  dataApi: DataApi,
  dataHelpers: DataHelpers,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer
) extends AdminController
  with PermissionHolder[UserProfile]
  with ItemPermissions[UserProfile]
  with Read[UserProfile]
  with Update[UserProfileF,UserProfile]
  with Delete[UserProfile]
  with Membership[UserProfile]
  with SearchType[UserProfile]
  with Search
  with AccountHelpers
  with CsvHelpers {

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
        name=Messages("contentTypes.Group"),
        param="group",
        sort = FacetSort.Name
      )
    )
  }

  val form = models.UserProfile.form

  private val userRoutes = controllers.users.routes.UserProfiles


  private val groupMembershipForm = Form(Forms.single("group" -> Forms.list(Forms.nonEmptyText)))

  private def userPasswordForm = Form(
    Forms.tuple(
      "email" -> Forms.email,
      "identifier" -> Forms.nonEmptyText(minLength= 3, maxLength = 20),
      "name" -> Forms.nonEmptyText,
      "password" -> Forms.nonEmptyText(minLength = minPasswordLength),
      "confirm" -> Forms.nonEmptyText(minLength = minPasswordLength)
    ) verifying("login.error.passwordsDoNotMatch", d => d._4 == d._5)
  )

  /**
   * Create a user's account for them with a pre-set password. This is an
   * admin only function and should be removed eventually.
   */
  def createUser = WithContentPermissionAction(PermissionType.Create, ContentTypes.UserProfile).async { implicit request =>
      dataHelpers.getGroupList.map { groups =>
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
   */
  def createUserPost = WithContentPermissionAction(PermissionType.Create, ContentTypes.UserProfile).async { implicit request =>
    dataHelpers.getGroupList.flatMap { allGroups =>
      userPasswordForm.bindFromRequest.fold(
        errorForm => immediate(BadRequest(views.html.admin.userProfile.create(
            errorForm,
            groupMembershipForm.bindFromRequest,
            allGroups,
            userRoutes.createUserPost())
          )
        ), { case (em, username, name, pw, _) =>
          saveUser(em, username, name, pw, allGroups)
        }
      )
    }
  }

  /**
   * Create a user's profile on the ReSt interface.
   */
  private def createUserProfile[T](user: UserProfileF, groups: Seq[String], allGroups: Seq[(String,String)])(
    implicit request: Request[T], userOpt: Option[UserProfile]): Future[Either[ValidationError,UserProfile]] = {
    userDataApi.create[UserProfile,UserProfileF](user, params = Map("group" -> groups)).map { item =>
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
    accounts.findByEmail(email.toLowerCase).flatMap {
      case Some(account) =>
        val errForm = userPasswordForm.bindFromRequest
          .withError(FormError("email", Messages("error.userEmailAlreadyRegistered", account.id)))
        immediate(BadRequest(views.html.admin.userProfile.create(errForm, groupMembershipForm.bindFromRequest,
          allGroups, userRoutes.createUserPost())))
      case None =>
        // It's not registered, so create the account...
        val user = UserProfileF(id = None, identifier = username, name = name,
          location = None, about = None, languages = Nil)
        val groups = (groupMembershipForm.bindFromRequest.value.getOrElse(List.empty) ++ defaultPortalGroups).distinct
        createUserProfile(user, groups, allGroups).flatMap {
          case Left(ValidationError(errorSet)) =>
            val errForm = user.getFormErrors(errorSet, userPasswordForm.bindFromRequest)
            immediate(BadRequest(views.html.admin.userProfile.create(errForm, groupMembershipForm.bindFromRequest,
              allGroups, userRoutes.createUserPost())))
          case Right(profile) => for {
            account <- accounts.create(Account(
              id = profile.id,
              email = email.toLowerCase,
              verified = true,
              active = true,
              staff = true,
              allowMessaging = true,
              password = Some(HashedPassword.fromPlain(pw))
            ))
            _ <- userDataApi.setItemPermissions(profile.id, ContentTypes.UserProfile,
              profile.id, List(PermissionType.Owner.toString))
          } yield Redirect(controllers.users.routes.UserProfiles.get(profile.id))
        }
    }
  }

  def get(id: String) = ItemMetaAction(id).apply {  implicit request =>
    Ok(views.html.admin.userProfile.show(request.item, request.annotations))
  }

  def search = SearchTypeAction(facetBuilder = entityFacets).async { implicit request =>
    accounts.findAllById(ids = request.result.page.items.map(_._1.id)).map { accs =>
      val pageWithAccounts = request.result.mapItems { case(up, hit) =>
        up.copy(account = accs.find(_.id == up.id)) -> hit
      }
      Ok(views.html.admin.userProfile.search(pageWithAccounts, userRoutes.search()))
    }
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list = ItemPageAction.apply { implicit request =>
    Ok(views.html.admin.userProfile.list(request.page, request.params))
  }

  def export = AdminAction.async { implicit request =>
    for {
      accounts <- accounts.findAll(PageParams.empty.withoutLimit)
      users <- userDataApi.list[UserProfile](PageParams.empty.withoutLimit)
    } yield {
      val datePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("YMMdd")
      val exportDate = ZonedDateTime.now().format(datePattern)
      val headers = Seq(
        "name", "email", "location", "url", "institution", "role", "about", "interests", "created"
      )
      val data: Seq[Array[String]] = for {
        user <- users
        account <- accounts.find(_.id == user.id)
      } yield {
        Array(
          user.model.name,
          account.email,
          user.model.location.getOrElse(""),
          user.model.url.orElse(user.model.workUrl).getOrElse(""),
          user.model.institution.getOrElse(""),
          user.model.role.getOrElse(""),
          user.model.about.getOrElse(""),
          user.model.interests.getOrElse(""),
          account.created.map(_.format(datePattern)).getOrElse("")
        )
      }
      Ok(writeCsv(headers, data))
        .as("text/csv")
        .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=users_$exportDate.csv")
    }
  }

  def update(id: String) = WithItemPermissionAction(id, PermissionType.Update).async { implicit request =>
    accounts.findById(request.item.id).map { accountOpt =>
      val userWithAccount = request.item.copy(account = accountOpt)
      Ok(views.html.admin.userProfile.edit(request.item, AdminUserData.form.fill(
        AdminUserData.fromUserProfile(userWithAccount)),
        userRoutes.updatePost(id)))
    }
  }

  def updatePost(id: String) = WithItemPermissionAction(id, PermissionType.Update).async { implicit request =>
    accounts.findById(request.item.id).flatMap { accountOpt =>
      val userWithAccount = request.item.copy(account = accountOpt)
      AdminUserData.form.bindFromRequest.fold(
        errForm => immediate(BadRequest(views.html.admin.userProfile.edit(userWithAccount, errForm,
          userRoutes.updatePost(id)))),
        data => accountOpt match {
          case Some(account) => for {
            profile <- userDataApi.patch[UserProfile](id, Json.toJson(data).as[JsObject])
            newAccount <- accounts.update(account.copy(active = data.active, staff = data.staff))
          } yield Redirect(userRoutes.search())
              .flashing("success" -> Messages("item.update.confirmation", request.item.toStringLang))
          case None => for {
            profile <- userDataApi.patch[UserProfile](id, Json.toJson(data).as[JsObject])
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
        accounts.findById(id).flatMap {
          case Some(account) => for {
            _ <- userDataApi.delete[UserProfile](id, logMsg = getLogMessage)
            _ <- accounts.delete(id)
          } yield Redirect(userRoutes.search())
              .flashing("success" -> Messages("item.delete.confirmation", id))
          case None => for {
            _ <- userDataApi.delete[UserProfile](id, logMsg = getLogMessage)
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
        request.item, request.accessor, request.itemPermissions,
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

