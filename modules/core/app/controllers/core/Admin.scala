package controllers.core

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import play.api.data.Form
import play.api.data.Forms._
import defines.{EntityType, PermissionType, ContentTypes}
import play.api.i18n.Messages
import models.{UserProfile,UserProfileF,Account,AccountDAO}
import controllers.base.{ControllerHelpers, AuthController}

import com.google.inject._
import jp.t2v.lab.play2.auth.LoginLogout
import play.api.Play.current
import scala.concurrent.Await
import play.api.Logger
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import models.json.RestResource
import backend.rest.{RestHelpers, ValidationError}
import play.api.data.FormError

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import backend.Backend
import controllers.core.auth.openid.OpenIDLoginHandler
import controllers.core.auth.userpass.UserPasswordLoginHandler
import models.sql.SqlAccount
import java.util.UUID

/**
 * Controller for handling user admin actions.
 */
case class Admin @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO)
  extends Controller
  with AuthController
  with OpenIDLoginHandler
  with UserPasswordLoginHandler
  with LoginLogout
  with ControllerHelpers {

  implicit def resource = new RestResource[UserProfile] {
    val entityType = EntityType.UserProfile
  }

  override val staffOnly = true


  private val groupMembershipForm = Form(single("group" -> list(nonEmptyText)))

  private val userPasswordForm = Form(
    tuple(
      "email" -> email,
      "identifier" -> nonEmptyText(minLength= 3, maxLength = 20),
      "name" -> nonEmptyText,
      "password" -> nonEmptyText(minLength = 6),
      "confirm" -> nonEmptyText(minLength = 6)
    ) verifying("login.passwordsDoNotMatch", f => f match {
      case (_, _, _, pw, pwc) => pw == pwc
    })
  )

  /**
   * Create a user's account for them with a pre-set password. This is an
   * admin only function and should be removed eventually.
   */
  def createUser = withContentPermission.async(PermissionType.Create, ContentTypes.UserProfile) {
      implicit userOpt => implicit request =>
    getGroups { groups =>
      Ok(views.html.admin.createUser(userPasswordForm, groupMembershipForm, groups,
        controllers.core.routes.Admin.createUserPost()))
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
  def createUserPost = withContentPermission.async(PermissionType.Create, ContentTypes.UserProfile) {
      implicit userOpt => implicit request =>

    // Blocking! This helps simplify the nest of callbacks.
    val allGroups: List[(String, String)] = Await.result(
      RestHelpers.getGroupList, Duration(1, TimeUnit.MINUTES))

    userPasswordForm.bindFromRequest.fold(
      errorForm => {
          immediate(Ok(views.html.admin.createUser(errorForm, groupMembershipForm.bindFromRequest,
              allGroups, controllers.core.routes.Admin.createUserPost())))
      },
      {
        case (em, username, name, pw, _) =>
          saveUser(em, username, name, pw, allGroups)
      }
    )
  }

  /**
   *  Grant a user permissions on their own account.
   */
  private def grantOwnerPerms[T](profile: UserProfile)(f: => SimpleResult)(
    implicit request: Request[T], userOpt: Option[UserProfile]): Future[SimpleResult] = {
    backend.setItemPermissions(profile, ContentTypes.UserProfile,
        profile.id, List(PermissionType.Owner.toString)).map { perms =>
      f
    }
  }

  /**
   * Create a user's profile on the ReSt interface.
   */
  private def createUserProfile[T](user: UserProfileF, groups: Seq[String], allGroups: List[(String,String)])(f: UserProfile => Future[SimpleResult])(
     implicit request: Request[T], userOpt: Option[UserProfile]): Future[SimpleResult] = {
    backend.create[UserProfile,UserProfileF](user, params = Map("group" -> groups)).flatMap { item =>
      f(item)
    } recoverWith {
      case ValidationError(errorSet) => {
        val errForm = user.getFormErrors(errorSet, userPasswordForm.bindFromRequest)
        immediate(BadRequest(views.html.admin.createUser(errForm, groupMembershipForm.bindFromRequest,
          allGroups, controllers.core.routes.Admin.createUserPost())))
      }
    }
  }

  /**
   * Save a user, creating both an account and a profile.
   */
  private def saveUser[T](email: String, username: String, name: String, pw: String, allGroups: List[(String, String)])(
    implicit request: Request[T], userOpt: Option[UserProfile]): Future[SimpleResult] = {
    // check if the email is already registered...
    userDAO.findByEmail(email.toLowerCase).map { account =>
      val errForm = userPasswordForm.bindFromRequest
        .withError(FormError("email", Messages("admin.userEmailAlreadyRegistered", account.id)))
      immediate(BadRequest(views.html.admin.createUser(errForm, groupMembershipForm.bindFromRequest,
        allGroups, controllers.core.routes.Admin.createUserPost())))
    } getOrElse {
      // It's not registered, so create the account...
      val user = UserProfileF(id = None, identifier = username, name = name,
        location = None, about = None, languages = Nil)
      val groups = groupMembershipForm.bindFromRequest.value.getOrElse(List())

      createUserProfile(user, groups, allGroups) { profile =>
        userDAO.createWithPassword(profile.id, email.toLowerCase, verified = true,
            staff = true, allowMessaging = true, Account.hashPassword(pw))
        grantOwnerPerms(profile) {
          Redirect(controllers.core.routes.UserProfiles.search())
        }
      }
    }
  }
}