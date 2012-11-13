package rest

import play.api.libs.json.JsValue
import defines.EntityType
import play.api.libs.concurrent.execution.defaultContext
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
    rest.cypher.CypherDAO()
              .cypher("START n=node:%s('identifier:*') RETURN n.identifier, n.name".format(EntityType.Group)).map { goe =>
      if (goe.isLeft) sys.error("Unable to fetch user list: " + goe.left.get)
      parseUsers(goe.right.get)
    }    
  }
  
  def getUserList: Future[List[(String,String)]] = {
    rest.cypher.CypherDAO()
              .cypher("START n=node:%s('identifier:*') RETURN n.identifier, n.name".format(EntityType.UserProfile)).map { goe =>
      if (goe.isLeft) sys.error("Unable to fetch user list: " + goe.left.get)
      parseUsers(goe.right.get)
    }    
  }  
}