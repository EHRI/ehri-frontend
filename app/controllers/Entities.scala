package controllers

import com.codahale.jerkson.Json.generate
import rest._
import play.api.libs.concurrent.execution.defaultContext
import play.api.mvc.Controller
import models.DocumentaryUnit
import models.AccessibleEntity
import play.api.data.Form
import play.api.mvc.Action
import play.api.mvc.SimpleResult
import play.api.mvc.AnyContent
import play.api.mvc.Result
import scala.concurrent.Future
import play.api.mvc.RequestHeader
import defines.EntityType

object Entities extends Controller with AuthController with ControllerHelpers {

  def list(entityType: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(EntityType.withName(entityType), maybeUser.flatMap(_.profile)).list.map { itemOrErr =>
          itemOrErr.right.map { lst => Ok(views.html.entities.list(lst)) }
        }
      }
  }

  def getJson(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(EntityType.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item => Ok(generate(item.data)) }
        }
      }
  }

  def get(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        EntityDAO(EntityType.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item => Ok(views.html.entities.show(item)) }
        }
      }
  }

  def create(entityType: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      val form = forms.formFor(EntityType.withName(entityType))
      val view = views.html.documentaryUnit.edit //(EntityTypes.withName(entityType))
      val action = routes.Entities.createPost(entityType)
      Ok(view(None, form, action))
  }

  def createPost(entityType: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      val form = forms.formFor(EntityType.withName(entityType)).bindFromRequest
      val view = views.html.documentaryUnit.edit //(EntityTypes.withName(entityType))
      val action = routes.Entities.createPost(entityType)
      form.fold(
        errorForm => BadRequest(view(None, errorForm, action)),
        doc => {
          AsyncRest {
            EntityDAO(EntityType.withName(entityType), maybeUser.flatMap(_.profile))
              .create(doc.toData).map { itemOrErr =>
                itemOrErr.right.map { item => Redirect(routes.Entities.get(entityType, item.identifier)) }
              }
          }
        }
      )
  }

  def update(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      val form = forms.formFor(EntityType.withName(entityType))
      val view = views.html.documentaryUnit.edit //(EntityTypes.withName(entityType))
      val action = routes.Entities.updatePost(entityType, id)
      AsyncRest {
        EntityDAO(EntityType.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item =>
            val doc: DocumentaryUnit = DocumentaryUnit(item)
            Ok(view(Some(doc), form.fill(doc), action))
          }
        }
      }
  }

  def updatePost(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      val form = forms.formFor(EntityType.withName(entityType)).bindFromRequest
      val view = views.html.documentaryUnit.edit //(EntityTypes.withName(entityType))
      val action = routes.Entities.updatePost(entityType, id)
      form.fold(
        errorForm => {
          AsyncRest {
            EntityDAO(EntityType.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
              itemOrErr.right.map { item =>
                val doc: DocumentaryUnit = DocumentaryUnit(item)
                BadRequest(view(Some(doc), errorForm, action))
              }
            }
          }
        },
        doc => {
          AsyncRest {
            EntityDAO(EntityType.withName(entityType), maybeUser.flatMap(_.profile))
              .update(id, doc.toData).map { itemOrErr =>
                itemOrErr.right.map { item =>
                  Redirect(routes.Entities.get(entityType, item.identifier))
                }
              }
          }
        }
      )
  }

  def delete(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      val view = views.html.delete //(EntityTypes.withName(entityType))
      val action = routes.Entities.deletePost(entityType, id)
      AsyncRest {
        EntityDAO(EntityType.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item =>
            val doc: DocumentaryUnit = DocumentaryUnit(item)
            Ok(view(doc, action))

          }
        }
      }
  }

  def deletePost(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      val view = views.html.delete
      val action = routes.Entities.deletePost(entityType, id)
      AsyncRest {
        EntityDAO(EntityType.withName(entityType), maybeUser.flatMap(_.profile)).delete(id).map { boolOrErr =>
          boolOrErr.right.map { ok => Redirect(routes.Entities.list(entityType)) }
        }
      }
  }
}