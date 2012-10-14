package controllers

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.ws.WS
import play.api.mvc.SimpleResult
import play.api.mvc.ResponseHeader
import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.execution.defaultContext
import models.{ EntityDAO, EntityTypes }
import play.api.libs.json.Json
import com.codahale.jerkson.Json.generate
import models.PermissionDenied
import views.html.defaultpages.unauthorized
import models.ItemNotFound
import models.ValidationError

object Entities extends Controller with AuthController {

  def list(entityType: String) = userProfileAction { implicit maybeUser =>
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

  def getJson(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      Async {
        EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr match {
            case Right(item) => Ok(generate(item.data))
            case Left(err) => err match {
              case PermissionDenied => Unauthorized(err.toString())
              case ItemNotFound => NotFound(err.toString())
              case ValidationError => BadRequest(err.toString())
              case _ => BadRequest(err.toString())
            }
          }
        }
      }
  }

  def get(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      Async {
        EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr match {
            case Right(item) => Ok(item.toString)
            case Left(err) => err match {
              case PermissionDenied => Unauthorized(err.toString())
              case ItemNotFound => NotFound(err.toString())
              case ValidationError => BadRequest(err.toString())
              case _ => BadRequest(err.toString())
            }
          }
        }
      }
  }

  def create(entityType: String) = TODO

  def createPost(entityType: String) = TODO

  def update(entityType: String, id: Long) = TODO

  def updatePost(entityType: String, id: Long) = TODO

  def delete(entityType: String, id: Long) = TODO

  def deletePost(entityType: String, id: Long) = TODO
}