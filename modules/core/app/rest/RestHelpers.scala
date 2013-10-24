package rest

import play.api.libs.json.JsValue
import defines.EntityType
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future


object RestHelpers {

  def parseUsers(json: JsValue): List[(String, String)] = {
    (json \ "data").as[List[List[String]]].flatMap { lst =>
      lst match {
        case x :: y :: _ => Some((x, y))
        case _ => None
      }
    }
  }

  def getGroupList: Future[List[(String,String)]] = {
    rest.cypher.CypherDAO(None)
              .cypher("START n=node:entities('__ISA__:%s') RETURN n.__ID__, n.name".format(EntityType.Group)).map { goe =>
      parseUsers(goe)
    }    
  }
  
  def getUserList: Future[List[(String,String)]] = {
    rest.cypher.CypherDAO(None)
              .cypher("START n=node:entities('__ISA__:%s') RETURN n.__ID__, n.name".format(EntityType.UserProfile)).map { goe =>
      parseUsers(goe)
    }    
  }  
}