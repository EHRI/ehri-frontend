package controllers

import play.Plugin
import jp.t2v.lab.play20.auth.LoginLogout
import play.api._
import play.api.mvc._
import jp.t2v.lab.play20.auth.Auth

trait LoginHandler extends Plugin {
  
	def login: Action[play.api.mvc.AnyContent]
  
	def loginPost: Action[play.api.mvc.AnyContent]
}