package backend.rest

import play.api.cache.CacheApi
import play.api.libs.json.JsValue
import defines.EntityType
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import backend.rest.cypher.Cypher


trait RestHelpers {

  implicit def app: play.api.Application
  implicit def cache: CacheApi
  def cypher: Cypher

  private def parseIds(json: JsValue): Seq[(String, String)] = {
    (json \ "data").as[Seq[Seq[String]]].flatMap {
      case x :: y :: _ => Some((x, y))
      case _ => None
    }
  }

  private def getTypeIdAndName(s: EntityType.Value): Future[Seq[(String, String)]] =
    cypher.cypher(s"MATCH (n:$s) RETURN n.__ID__, n.name").map(parseIds)

  def getGroupList: Future[Seq[(String,String)]] = getTypeIdAndName(EntityType.Group)
  
  def getUserList: Future[Seq[(String,String)]] = getTypeIdAndName(EntityType.UserProfile)
}
