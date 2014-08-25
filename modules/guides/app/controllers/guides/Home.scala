package controllers.guides

import play.api.mvc._

import models.AccountDAO
import controllers.base.{ControllerHelpers, AuthController}

import com.google.inject._

import backend.Backend


/**
 * Controller for handling user admin actions.
 */
case class Home @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO)
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