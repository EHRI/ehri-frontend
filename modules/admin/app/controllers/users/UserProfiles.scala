package controllers.users

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject._

import auth.HashedPassword
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.{ContentTypes, EntityType, PermissionType}
import models._
import play.api.data.{Form, FormError, Forms}
import play.api.http.HeaderNames
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc._
import services.accounts.AccountFilters
import services.data.{DataHelpers, ValidationError}
import services.search._
import utils.{CsvHelpers, PageParams, RangeParams}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class UserProfiles @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers
) extends AdminController
  with PermissionHolder[UserProfile]
  with ItemPermissions[UserProfile]
  with Read[UserProfile]
  with Update[UserProfile]
  with Delete[UserProfile]
  with Membership[UserProfile]
  with SearchType[UserProfile]
  with Search
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
      QueryFacetClass(
        key="staff",
        name=Messages("userProfile.staff"),
        param="staff",
        render=s => Messages("userProfile.staff." + s),
        facets=List(
          QueryFacet(value = "true", range = Val("1"))
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

  private val userRoutes = controllers.users.routes.UserProfiles
  private val groupMembershipForm = Form(Forms.single("group" -> Forms.list(Forms.nonEmptyText)))

  private def userPasswordForm = Form(
    Forms.tuple(
      "email" -> Forms.email,
      "identifier" -> Forms.nonEmptyText(minLength= 3, maxLength = 20),
      "name" -> Forms.nonEmptyText,
      "password" -> Forms.nonEmptyText(minLength = conf.minPasswordLength),
      "confirm" -> Forms.nonEmptyText(minLength = conf.minPasswordLength)
    ) verifying("login.error.passwordsDoNotMatch", d => d._4 == d._5)
  )

  /**
   * Create a user's account for them with a pre-set password. This is an
   * admin only function and should be removed eventually.
   */
  def createUser: Action[AnyContent] = WithContentPermissionAction(PermissionType.Create, ContentTypes.UserProfile).async { implicit request =>
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
  def createUserPost: Action[AnyContent] = WithContentPermissionAction(PermissionType.Create, ContentTypes.UserProfile).async { implicit request =>
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
        val groups = (groupMembershipForm.bindFromRequest.value
          .getOrElse(List.empty) ++ conf.defaultPortalGroups).distinct
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
              staff = true,
              password = Some(HashedPassword.fromPlain(pw))
            ))
            _ <- userDataApi.setItemPermissions(profile.id, ContentTypes.UserProfile,
              profile.id, List(PermissionType.Owner.toString))
          } yield Redirect(controllers.users.routes.UserProfiles.get(profile.id))
        }
    }
  }

  def get(id: String): Action[AnyContent] = ItemMetaAction(id).async {  implicit request =>
    accounts.findById(request.item.id).map { accountOpt =>
      val userWithAccount = request.item.copy(account = accountOpt)
      Ok(views.html.admin.userProfile.show(userWithAccount, request.annotations))
    }
  }

  def search(params: SearchParams, paging: PageParams): Action[AnyContent] =
    SearchTypeAction(params, paging, facetBuilder = entityFacets).async { implicit request =>
      accounts.findAllById(ids = request.result.page.items.map(_._1.id)).map { accs =>
        val pageWithAccounts = request.result.mapItems { case(up, hit) =>
          up.copy(account = accs.find(_.id == up.id)) -> hit
        }
        Ok(views.html.admin.userProfile.search(pageWithAccounts, userRoutes.search()))
      }
    }

  def history(id: String, range: RangeParams): Action[AnyContent] = ItemHistoryAction(id, range).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list(paging: PageParams): Action[AnyContent] = ItemPageAction(paging).apply { implicit request =>
    Ok(views.html.admin.userProfile.list(request.page, request.params))
  }

  def export: Action[AnyContent] = AdminAction.async { implicit request =>
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
          user.data.name,
          account.email,
          user.data.location.getOrElse(""),
          user.data.url.orElse(user.data.workUrl).getOrElse(""),
          user.data.institution.getOrElse(""),
          user.data.role.getOrElse(""),
          user.data.about.getOrElse(""),
          user.data.interests.getOrElse(""),
          account.created.map(_.format(datePattern)).getOrElse("")
        )
      }
      Ok.chunked(writeCsv(headers, data))
        .as("text/csv")
        .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=users_$exportDate.csv")
    }
  }

  def update(id: String): Action[AnyContent] =
    WithItemPermissionAction(id, PermissionType.Update).async { implicit request =>
      accounts.findById(request.item.id).map { accountOpt =>
        val userWithAccount = request.item.copy(account = accountOpt)
        Ok(views.html.admin.userProfile.edit(userWithAccount, AdminUserData.form.fill(
          AdminUserData.fromUserProfile(userWithAccount)),
          userRoutes.updatePost(id)))
      }
    }

  def updatePost(id: String): Action[AnyContent] =
    WithItemPermissionAction(id, PermissionType.Update).async { implicit request =>
      accounts.findById(request.item.id).flatMap { accountOpt =>
        val userWithAccount = request.item.copy(account = accountOpt)
        AdminUserData.form.bindFromRequest.fold(
          errForm => immediate(BadRequest(views.html.admin.userProfile.edit(userWithAccount, errForm,
            userRoutes.updatePost(id)))),
          data => accountOpt match {
            case Some(account) => for {
              _ <- userDataApi.patch[UserProfile](id, Json.toJson(data).as[JsObject])
              _ <- accounts.update(account.copy(
                active = data.active, staff = data.staff, verified = data.verified))
            } yield Redirect(userRoutes.search())
                .flashing("success" -> Messages("item.update.confirmation", request.item.toStringLang))
            case None => for {
              _ <- userDataApi.patch[UserProfile](id, Json.toJson(data).as[JsObject])
            } yield Redirect(userRoutes.search())
                .flashing("success" -> Messages("item.update.confirmation", request.item.toStringLang))
          }
        )
      }
    }

  private def deleteForm(user: UserProfile): Form[String] = Form(
    Forms.single("deleteCheck" -> Forms.nonEmptyText
      .verifying("error.invalidName", name => name == user.data.name))
  )

  def delete(id: String): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.userProfile.delete(
      request.item, deleteForm(request.item), userRoutes.deletePost(id),
          userRoutes.get(id)))
  }

  def deletePost(id: String): Action[AnyContent] =
    WithItemPermissionAction(id, PermissionType.Delete).async { implicit request =>
      deleteForm(request.item).bindFromRequest.fold(
        errForm => {
          immediate(BadRequest(views.html.admin.userProfile.delete(
            request.item, errForm, userRoutes.deletePost(id),
            userRoutes.get(id))))
        },
        _ => {
          accounts.findById(id).flatMap {
            case Some(account) => for {
              _ <- userDataApi.delete[UserProfile](account.id, logMsg = getLogMessage)
              _ <- accounts.delete(account.id)
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

  def syncFromDbPost: Action[AnyContent] = AdminAction.async { implicit request =>
    for {
      staff <- accounts.findAll(
        filters = AccountFilters(staff = Some(true), active = Some(true)),
        params = PageParams.empty.withoutLimit)
      ids = JsArray(staff.map(a => JsString(a.id)))
      out <-  dataHelpers.cypher.get(
        """
          |MATCH (u:UserProfile)
          |WHERE u.__id IN {ids}
          |SET u.staff = true, u.active = true
          |RETURN u.__id as id
        """.stripMargin, Map("ids" -> ids))
    } yield Ok(Json.toJson(out))
  }

  def grantList(id: String, paging: PageParams): Action[AnyContent] = GrantListAction(id, paging).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionGrantList(
      request.item, request.permissionGrants))
  }

  def permissions(id: String): Action[AnyContent] = CheckGlobalPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.editGlobalPermissions(
      request.item, request.permissions,
        userRoutes.permissionsPost(id)))
  }

  def permissionsPost(id: String): Action[AnyContent] = SetGlobalPermissionsAction(id).apply { implicit request =>
    Redirect(userRoutes.get(id))
        .flashing("success" -> Messages("item.update.confirmation", id))
  }

  def revokePermission(id: String, permId: String): Action[AnyContent] = {
    CheckRevokePermissionAction(id, permId).apply { implicit request =>
      Ok(views.html.admin.permissions.revokePermission(
        request.item, request.permissionGrant,
        userRoutes.revokePermissionPost(id, permId), userRoutes.grantList(id)))
    }
  }

  def revokePermissionPost(id: String, permId: String): Action[AnyContent] = {
    RevokePermissionAction(id, permId).apply { implicit request =>
      Redirect(userRoutes.grantList(id))
        .flashing("success" -> Messages("item.delete.confirmation", id))
    }
  }

  def managePermissions(id: String, paging: PageParams): Action[AnyContent] =
    PermissionGrantAction(id, paging).apply { implicit request =>
      Ok(views.html.admin.permissions.managePermissions(request.item, request.permissionGrants,
          userRoutes.addItemPermissions(id)))
    }

  def addItemPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.usersAndGroups,
        userRoutes.setItemPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        userRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(userRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def membership(id: String): Action[AnyContent] = MembershipAction(id).apply { implicit request =>
    Ok(views.html.admin.group.membership(request.item, request.groups))
  }

  def checkAddToGroup(id: String, groupId: String): Action[AnyContent] =
    CheckManageGroupAction(id, groupId).apply { implicit request =>
      Ok(views.html.admin.group.confirmMembership(request.group, request.item,
        userRoutes.addToGroup(id, groupId)))
    }

  def addToGroup(id: String, groupId: String): Action[AnyContent] =
    AddToGroupAction(id, groupId).apply { implicit request =>
      Redirect(userRoutes.membership(id))
        .flashing("success" -> "item.update.confirmation")
    }

  def checkRemoveFromGroup(id: String, groupId: String): Action[AnyContent] =
    CheckManageGroupAction(id, groupId).apply { implicit request =>
      Ok(views.html.admin.group.removeMembership(request.group, request.item,
        userRoutes.removeFromGroup(id, groupId)))
    }

  def removeFromGroup(id: String, groupId: String): Action[AnyContent] =
    RemoveFromGroupAction(id, groupId).apply { implicit request =>
      Redirect(userRoutes.membership(id))
        .flashing("success" -> "item.update.confirmation")
    }
}

