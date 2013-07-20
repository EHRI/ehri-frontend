package controllers

import play.api.mvc._
import jp.t2v.lab.play20.auth.Auth
import base.{Authorizer,AuthController}


object Application extends Controller with Auth with Authorizer with AuthController {


}