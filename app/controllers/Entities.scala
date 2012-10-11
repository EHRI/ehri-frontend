package controllers

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.ws.WS
import play.api.mvc.SimpleResult
import play.api.mvc.ResponseHeader
import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.execution.defaultContext
import models.{EntityDAO,EntityTypes}
import play.api.libs.json.Json
import com.codahale.jerkson.Json.generate
import models.EntityWriter

object Entities extends Controller with AuthController {

  def list(entityType: String) = optionalUserAction { implicit maybeUser =>
    implicit request =>
      Async {
        EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).list.map { itemOrErr =>
          itemOrErr match {
            case Right(lst) => Ok(lst.toString)
            case Left(err) => BadRequest("WRONG!" + err)
          }
        }
      }
  }

  def getJson(entityType: String, id: String) = optionalUserAction { implicit maybeUser =>
    implicit request =>
      Async {
        EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr match {
            case Right(item) => Ok(EntityWriter.entityWrites.writes(item))
            case Left(err) => BadRequest("WRONG!" + err)
          }
        }
      }
  }

  def get(entityType: String, id: String) = optionalUserAction { implicit maybeUser =>
    implicit request =>
      Async {
        EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr match {
            case Right(item) => Ok(item.toString)
            case Left(err) => BadRequest("WRONG!" + err)
          }
        }
      }
  }

  def create(entityType: String) = TODO

  def createPost(entityType: String) = TODO

  def update(entityType: String, id: String) = TODO

  def updatePost(entityType: String, id: String) = TODO

  def delete(entityType: String, id: String) = TODO

  def deletePost(entityType: String, id: String) = TODO
}