package controllers

import com.codahale.jerkson.Json.generate
import rest._
import models.EntityTypes
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

object Entities extends Controller with AuthController with ControllerHelpers {

  def list(entityType: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      Async {
        WrapRest {
          EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).list.map { itemOrErr =>
            itemOrErr.fold(
              err => throw err,
              lst => Ok(views.html.entities.list(lst))
            )
          }
        }
      }
  }

  def getJson(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      Async {
        WrapRest {
          EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
            itemOrErr.fold(
              err => throw err,
              item => Ok(generate(item.data))
            )
          }
        }
      }
  }

  def get(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      Async {
        WrapRest {
          EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
            itemOrErr match {
              case Right(item) => Ok(views.html.entities.show(item))
              case Left(err) => throw err
            }
          }
        }
      }
  }

  def create(entityType: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      val form = forms.formFor(EntityTypes.withName(entityType))
      val view = views.html.documentaryUnit.edit //(EntityTypes.withName(entityType))
      val action = routes.Entities.createPost(entityType)
      Ok(view(None, form, action))
  }

  def createPost(entityType: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      val form = forms.formFor(EntityTypes.withName(entityType)).bindFromRequest
      val view = views.html.documentaryUnit.edit //(EntityTypes.withName(entityType))
      val action = routes.Entities.createPost(entityType)
      form.fold(
        errorForm => BadRequest(view(None, errorForm, action)),
        doc => {
          Async {
            WrapRest {
              EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile))
                .create(doc.toData).map { itemOrErr =>
                  itemOrErr match {
                    case Right(item) => Redirect(routes.Entities.get(entityType, item.identifier))
                    case Left(err) => throw err
                  }
                }
            }
          }
        }
      )
  }

  def update(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      val form = forms.formFor(EntityTypes.withName(entityType))
      val view = views.html.documentaryUnit.edit //(EntityTypes.withName(entityType))
      val action = routes.Entities.updatePost(entityType, id)
      Async {
        WrapRest {
          EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
            itemOrErr match {
              case Right(item) => {
                val doc: DocumentaryUnit = DocumentaryUnit(item)
                Ok(view(Some(doc), form.fill(doc), action))
              }
              case Left(err) => throw err
            }
          }
        }
      }
  }

  def updatePost(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      val form = forms.formFor(EntityTypes.withName(entityType)).bindFromRequest
      val view = views.html.documentaryUnit.edit //(EntityTypes.withName(entityType))
      val action = routes.Entities.updatePost(entityType, id)
      form.fold(
        errorForm => {
          Async {
            WrapRest {
              EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
                itemOrErr match {
                  case Right(item) => {
                    val doc: DocumentaryUnit = DocumentaryUnit(item)
                    BadRequest(view(Some(doc), errorForm, action))
                  }
                  case Left(err) => throw err
                }
              }
            }
          }
        },
        doc => {
          Async {
            WrapRest {
              println("Sending data: " + doc.toData)
              EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile))
                .update(id, doc.toData).map { itemOrErr =>
                  itemOrErr match {
                    case Right(item) => Redirect(routes.Entities.get(entityType, item.identifier))
                    case Left(err) => throw err
                  }
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
      Async {
        WrapRest {
          EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
            itemOrErr match {
              case Right(item) => {
                val doc: DocumentaryUnit = DocumentaryUnit(item)
                Ok(view(doc, action))
              }
              case Left(err) => throw err
            }
          }
        }
      }
  }

  def deletePost(entityType: String, id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      val view = views.html.delete //(EntityTypes.withName(entityType))
      val action = routes.Entities.deletePost(entityType, id)
      Async {
        WrapRest {
          EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).delete(id).map { boolOrErr =>
            boolOrErr match {
              case Right(res) => {
                Redirect(routes.Entities.list(entityType))
              }
              case Left(err) => throw err
            }
          }
        }
      }
  }
}