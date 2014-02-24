package controllers.guides

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import play.api.data.Form
import play.api.data.Forms._
import defines.{EntityType, PermissionType, ContentTypes}
import play.api.i18n.Messages
import models.{UserProfile,UserProfileF,Account,AccountDAO}
import controllers.base.{ControllerHelpers, AuthController}

import com.google.inject._
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
import java.util.UUID

/**
 * Controller for handling user admin actions.
 */
case class Home @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend)
  extends Controller
  with AuthController
  with ControllerHelpers {

	def places = Action { implicit request =>
		Ok(views.html.places())
	}
	
	def timeBrowse = Action { implicit request =>
		Ok(views.html.time())
	}
	def keywordBrowse = Action { implicit request =>
		Ok(views.html.keywords())
	}
	def personBrowse = Action { implicit request =>
		Ok(views.html.person())
	}
	def resultsBrowse = Action { implicit request =>
		Ok(views.html.results())
	}
	def itemShow = Action { implicit request =>
		Ok(views.html.item())
	}
	def organizationsShow = Action { implicit request =>
		Ok(views.html.organizations())
	}

}