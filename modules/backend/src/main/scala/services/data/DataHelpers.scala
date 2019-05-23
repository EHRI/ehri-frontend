package services.data

import defines.EntityType
import javax.inject.{Inject, Singleton}
import models.UsersAndGroups
import play.api.libs.json.JsString
import services.cypher.{CypherResult, CypherService}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
case class DataHelpers @Inject()(cypher: CypherService)(implicit executionContext: ExecutionContext) {

  private def parseIds(res: CypherResult): Seq[(String, String)] = {
    res.data.collect {
      case JsString(id) :: JsString(name) :: _ => id -> name
    }
  }

  private def getTypeIdAndName(s: EntityType.Value): Future[Seq[(String, String)]] =
    cypher.get(s"MATCH (n:$s) RETURN n.__id, n.name ORDER BY n.name", Map.empty).map(parseIds)

  def getGroupList: Future[Seq[(String,String)]] = getTypeIdAndName(EntityType.Group)
  
  def getUserList: Future[Seq[(String,String)]] = cypher
    .get(
      s"""
         |MATCH (n:${EntityType.UserProfile})
         |WHERE n.active AND n.staff
         |RETURN n.__id, n.name
         |ORDER BY n.name
         |""".stripMargin, Map.empty)
    .map(parseIds)

  def getUserAndGroupList: Future[UsersAndGroups] = {
    val usersF = getUserList
    val groupsF = getGroupList
    for (users <- usersF; groups <- groupsF) yield UsersAndGroups(users, groups)
  }
}
