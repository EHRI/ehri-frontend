package backend.rest

import play.api.libs.json.{JsString, JsValue}
import defines.EntityType
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import backend.rest.cypher.CypherDAO


trait RestHelpers {

  def parseUsers(json: JsValue): List[(String, String)] = {
    (json \ "data").as[List[List[String]]].flatMap {
      case x :: y :: _ => Some((x, y))
      case _ => None
    }
  }

  def getGroupList: Future[List[(String,String)]] = {
    cypher.CypherDAO()
              .cypher("START n=node:entities(__ISA__ = {isA}) RETURN n.__ID__, n.name",
        Map("isA" -> JsString(EntityType.Group))).map { goe =>
      parseUsers(goe)
    }    
  }
  
  def getUserList: Future[List[(String,String)]] = {
    cypher.CypherDAO()
              .cypher("START n=node:entities(__ISA__ = {isA}) RETURN n.__ID__, n.name",
        Map("isA" -> JsString(EntityType.UserProfile))).map { goe =>
      parseUsers(goe)
    }    
  }  
}

object RestHelpers extends RestHelpers