package models

import play.api.libs.concurrent.Promise
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future


object User {

  import play.api.http.Status.{OK, NOT_FOUND}
  
  def findByIdSync(id: String): Option[User] = {
    // FIXME: Crappy syncronous alternative to findById
    findById(id).value.get.get
  }
  
  def findById(id: String): Future[Option[User]] = {
    WS.url("http://localhost:7575/ehri/userProfile")
    	.withQueryString("key" -> "identifier", "value" -> id)
    	.withHeaders("Authorization" -> "21")
    	.get.map { response =>
       
       response.status match {
         case OK => Some(User(response.json))
         case NOT_FOUND => None
         case _ => throw new RuntimeException("Unexpected response from user findById!")
       }      
    }
  }
  
  def apply(json: JsValue): User = {
    EntityReader.entityReads.reads(json).fold(
      invalid = {
        throw new RuntimeException("Oh crap, parsing user failed!")
      },
      valid = { item =>
        User(item.property("identifier").map(_.toString).get, item.property("name").map(_.toString))
      }
    )
  }
}


case class User(val id: String, val name: Option[String]) {
  
}