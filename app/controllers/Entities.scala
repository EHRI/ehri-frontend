package controllers

import com.codahale.jerkson.Json.generate
import models.EntityDAO
import models.EntityTypes
import models.ItemNotFound
import models.PermissionDenied
import models.ValidationError
import play.api.libs.concurrent.execution.defaultContext
import play.api.mvc.Controller
import models.DocumentaryUnit

object Entities extends Controller with AuthController {

  def list(entityType: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      Async {
        EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).list.map { itemOrErr =>
          itemOrErr match {
            case Right(lst) => Ok(views.html.entities.list(lst))
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
              case PermissionDenied => Unauthorized(views.html.errors.permissionDenied())
              case ItemNotFound => NotFound(views.html.errors.itemNotFound())
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
            case Right(item) => Ok(views.html.entities.show(item))
            case Left(err) => err match {
              case PermissionDenied => Unauthorized(views.html.errors.permissionDenied())
              case ItemNotFound => NotFound(views.html.errors.itemNotFound())
              case ValidationError => BadRequest(err.toString())
              case _ => BadRequest(err.toString())
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
            println("Sending data: " + doc.toData)
        	EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile))
        		.create(doc.toData).map { itemOrErr =>
	          itemOrErr match {
	            case Right(item) => Redirect(routes.Entities.get(entityType, item.identifier))
	            case Left(err) => err match {
	              case PermissionDenied => Unauthorized(views.html.errors.permissionDenied())
	              case ItemNotFound => NotFound(views.html.errors.itemNotFound())
	              case ValidationError => BadRequest(err.toString())
	              case _ => BadRequest(err.toString())
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
        EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr match {
            case Right(item) => {
              val doc: DocumentaryUnit = DocumentaryUnit(item)
              Ok(view(Some(doc), form.fill(doc), action))
            }
            case Left(err) => err match {
              case PermissionDenied => Unauthorized(views.html.errors.permissionDenied())
              case ItemNotFound => NotFound(views.html.errors.itemNotFound())
              case ValidationError => BadRequest(err.toString())
              case _ => BadRequest(err.toString())
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
      Async {
        EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr match {
            case Right(item) => {
              val doc: DocumentaryUnit = DocumentaryUnit(item)

              form.fold(
                errorForm => BadRequest(view(Some(doc), errorForm, action)),
                doc => {
                  Async {
                    println("Sending data: " + doc.toData)
                	EntityDAO(EntityTypes.withName(entityType), maybeUser.flatMap(_.profile))
                		.update(id, doc.toData).map { itemOrErr =>
			          itemOrErr match {
			            case Right(item) => Redirect(routes.Entities.get(entityType, item.identifier))
			            case Left(err) => err match {
			              case PermissionDenied => Unauthorized(views.html.errors.permissionDenied())
			              case ItemNotFound => NotFound(views.html.errors.itemNotFound())
			              case ValidationError => BadRequest(err.toString())
			              case _ => BadRequest(err.toString())
			            }
			          }
                	}
                  }
                }
              )
            }
            case Left(err) => err match {
              case PermissionDenied => Unauthorized(views.html.errors.permissionDenied())
              case ItemNotFound => NotFound(views.html.errors.itemNotFound())
              case ValidationError => BadRequest(err.toString())
              case _ => BadRequest(err.toString())
            }
          }
        }
      }
  }

  def delete(entityType: String, id: String) = TODO

  def deletePost(entityType: String, id: String) = TODO
}