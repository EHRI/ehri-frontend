package controllers

import play.api._
import play.api.mvc._
import play.api.libs.ws.WS
import play.api.libs.json.Json

object Application extends Controller {
  
  val PERSONA_URL = "https://verifier.login.persona.org/verify"
  val EHRI_URL = "http://ehritest.dans.knaw.nl"
  
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }
  
  def signup = TODO
  
  def login = TODO
  
  def loginPost = TODO
  
  def logout = TODO
  
  
  
  /*
  def login(assertion: String) = {
    
    val data = Map(
        "assertion" -> assertion, "audience" -> EHRI_URL)
    
    Async {
      
      WS.url(PERSONA_URL).post(Json.toJson(data)).map { response => 
        response.status match {
          case OK => 
        }
      }      
    }
  }
  */
}