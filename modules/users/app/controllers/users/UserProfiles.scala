package controllers.users

import play.api.libs.concurrent.Execution.Implicits._
import controllers.generic._
import models._
import play.api.i18n.Messages
import defines.{PermissionType, ContentTypes}
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
import solr.facet.FieldFacetClass
import play.api.mvc.SimpleResult
import solr.facet.SolrQueryFacet
import play.api.libs.json.JsObject
import solr.facet.QueryFacetClass


@Singleton
case class UserProfiles @Inject()(implicit globalConfig: global.GlobalConfig, searchIndexer: Indexer, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends PermissionHolder[UserProfile]
  with Read[UserProfile]
  with Update[UserProfileF,UserProfile]
  with Delete[UserProfile]
  with Search {

  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key="active",
        name=Messages("userProfile.active"),
        param="staff",
        render=s => Messages("userProfile.active." + s),
        facets=List(
          SolrQueryFacet(value = "true", solrValue = "1"),
          SolrQueryFacet(value = "false", solrValue = "0")
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

  implicit val resource = UserProfile.Resource

  val contentType = ContentTypes.UserProfile

  // Search params
  val DEFAULT_SEARCH_PARAMS = SearchParams(entities = List(resource.entityType))

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
        Ok(views.html.userProfile.createUser(userPasswordForm, groupMembershipForm, groups,
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
  def createUserPost = withContentPermission.async(PermissionType.Create, ContentTypes.UserProfile) {
    implicit userOpt => implicit request =>

    // Blocking! This helps simplify the nest of callbacks.
      val allGroups: List[(String, String)] = Await.result(
        RestHelpers.getGroupList, Duration(1, TimeUnit.MINUTES))

      userPasswordForm.bindFromRequest.fold(
      errorForm => {
        immediate(Ok(views.html.userProfile.createUser(errorForm, groupMembershipForm.bindFromRequest,
          allGroups, userRoutes.createUserPost())))
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
        immediate(BadRequest(views.html.userProfile.createUser(errForm, groupMembershipForm.bindFromRequest,
          allGroups, userRoutes.createUserPost())))
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
      immediate(BadRequest(views.html.userProfile.createUser(errForm, groupMembershipForm.bindFromRequest,
        allGroups, userRoutes.createUserPost())))
    } getOrElse {
      // It's not registered, so create the account...
      val user = UserProfileF(id = None, identifier = username, name = name,
        location = None, about = None, languages = Nil)
      val groups = groupMembershipForm.bindFromRequest.value.getOrElse(List())

      createUserProfile(user, groups, allGroups) { profile =>
        userDAO.createWithPassword(profile.id, email.toLowerCase, verified = true,
          staff = true, allowMessaging = true, Account.hashPassword(pw))
        grantOwnerPerms(profile) {
          Redirect(controllers.users.routes.UserProfiles.search())
        }
      }
    }
  }

  def get(id: String) = getAction(id) {
      item => annotations => links => implicit userOpt => implicit request =>
    Ok(views.html.userProfile.show(item, annotations))
  }

  def search = {
    searchAction[UserProfile](defaultParams = Some(DEFAULT_SEARCH_PARAMS), entityFacets = entityFacets) {
        page => params => facets => implicit userOpt => implicit request =>
      // Crap alert! Lookup accounts for users. This is undesirable 'cos it's
      // one DB SELECT per user, but since it's just a management page it shouldn't
      // matter too much.
      val pageWithAccounts = page.copy(items = page.items.map { case(up, hit) =>
        (up.copy(account = userDAO.findByProfileId(up.id)), hit)
      })
      Ok(views.html.userProfile.search(pageWithAccounts, params, facets, userRoutes.search()))
    }
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOptOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOptOpt => implicit request =>
    Ok(views.html.userProfile.list(page, params))
  }

  def update(id: String) = withItemPermission[UserProfile](id, PermissionType.Update, contentType) {
      item => implicit userOpt => implicit request =>
    val userWithAccount = item.copy(account = userDAO.findByProfileId(id))
    Ok(views.html.userProfile.edit(item, AdminUserData.form.fill(
      AdminUserData.fromUserProfile(userWithAccount)),
      userRoutes.updatePost(id)))
  }

  def updatePost(id: String) = withItemPermission.async[UserProfile](id, PermissionType.Update, contentType) {
      item => implicit userOpt => implicit request =>
    val userWithAccount = item.copy(account = userDAO.findByProfileId(id))
    AdminUserData.form.bindFromRequest.fold(
      errForm => immediate(BadRequest(views.html.userProfile.edit(userWithAccount, errForm,
        userRoutes.updatePost(id)))),
      data => backend.patch[UserProfile](id, Json.toJson(data).as[JsObject]).map { updated =>
        userDAO.findByProfileId(id).map { acc =>
          acc.setActive(data.active).setStaff(data.staff)
        }
        Redirect(userRoutes.search())
          .flashing("success" -> Messages("confirmations.userWasDeactivated", item.toStringLang))
      }
    )
  }

  private def deleteForm(user: UserProfile): Form[String] = Form(
    Forms.single("deleteCheck" -> Forms.nonEmptyText.verifying("error.invalidName", f => f match {
      case name => {
        println(s"Names: $name vs ${user.model.name}")
        name == user.model.name
      }
    }))
  )

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.userProfile.delete(item, deleteForm(item), userRoutes.deletePost(id),
          userRoutes.get(id)))
  }

  def deletePost(id: String) = withItemPermission.async[UserProfile](id, PermissionType.Delete, contentType) {
      item => implicit userOpt => implicit request =>
    deleteForm(item).bindFromRequest.fold(
      errForm => {
        immediate(BadRequest(views.html.userProfile.delete(item, deleteForm(item), userRoutes.deletePost(id),
          userRoutes.get(id))))
      },
      ok => backend.delete[UserProfile](id, logMsg = getLogMessage).map { ok =>
        // For the users we need to clean up by deleting their profile id, if any...
        userDAO.findByProfileId(id).map(_.delete())
        Redirect(userRoutes.search())
          .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
      }
    )
  }

  def grantList(id: String) = grantListAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionGrantList(item, perms))
  }

  def permissions(id: String) = setGlobalPermissionsAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.editGlobalPermissions(item, perms,
        userRoutes.permissionsPost(id)))
  }

  def permissionsPost(id: String) = setGlobalPermissionsPostAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Redirect(userRoutes.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def revokePermission(id: String, permId: String) = revokePermissionAction(id, permId) {
      item => perm => implicit userOpt => implicit request =>
        Ok(views.html.permissions.revokePermission(item, perm,
          userRoutes.revokePermissionPost(id, permId), userRoutes.grantList(id)))
  }

  def revokePermissionPost(id: String, permId: String) = revokePermissionActionPost(id, permId) {
    item => bool => implicit userOpt => implicit request =>
      Redirect(userRoutes.grantList(id))
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }
}

