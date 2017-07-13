package services.rest

import javax.inject.{Inject, Singleton}

import play.api.libs.json.JsValue
import defines.EntityType

import scala.concurrent.{ExecutionContext, Future}
import services.rest.cypher.Cypher


@Singleton
case class DataHelpers @Inject()(cypher: Cypher)(implicit executionContext: ExecutionContext) {

  private def parseIds(json: JsValue): Seq[(String, String)] = {
    (json \ "data").as[Seq[Seq[String]]].flatMap {
      case x :: y :: _ => Some((x, y))
      case _ => None
    }
  }

  private def getTypeIdAndName(s: EntityType.Value): Future[Seq[(String, String)]] =
    cypher.cypher(s"MATCH (n:$s) RETURN n.__id, n.name").map(parseIds)

  def getGroupList: Future[Seq[(String,String)]] = getTypeIdAndName(EntityType.Group)
  
  def getUserList: Future[Seq[(String,String)]] = cypher
    .cypher(s"MATCH (n:${EntityType.UserProfile}) WHERE n.active AND n.staff RETURN n.__id, n.name")
    .map(parseIds)

  def getUserAndGroupList: Future[(Seq[(String, String)], Seq[(String, String)])] = {
    val usersF = getUserList
    val groupsF = getGroupList
    for (users <- usersF; groups <- groupsF) yield (users, groups)
  }
}
