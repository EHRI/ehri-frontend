package controllers

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.ws.WS
import play.api.mvc.SimpleResult
import play.api.mvc.ResponseHeader
import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.execution.defaultContext
import models.EntityReader
import play.api.libs.json.Json

object Entities extends Controller {

  val BASEURL = "http://localhost:7474/ehri"
  val HEADERS = Map("Authorization" -> "2")
  val CONTENT = Map(CONTENT_TYPE -> "application/json")

  def list(entityType: String) = Action {
    Async {
      WS.url(List(BASEURL, entityType, "list").mkString("/"))
        .withHeaders(HEADERS.toSeq: _*).get.map { response =>
          SimpleResult(
            header = ResponseHeader(response.status, CONTENT),
            body = Enumerator(response.json)
          )
        }
    }
  }

    def getJson(entityType: String, id: Long) = Action {
    Async {
      WS.url(List(BASEURL, entityType, id.toString).mkString("/"))
        .withHeaders(HEADERS.toSeq: _*).get.map { response =>
          SimpleResult(
            header = ResponseHeader(response.status, CONTENT),
            body = Enumerator(response.json)
          )
        }
    }
  }
  
  def get(entityType: String, id: Long) = Action {
    Async {
      WS.url(List(BASEURL, entityType, id.toString).mkString("/"))
        .withHeaders(HEADERS.toSeq: _*).get.map { response =>
          
          import models.EntityReader._
          import models.Entity
          
          entityReads.reads(response.json).fold(
            valid = { item =>
              Ok(item.toString)
            },
            invalid = { errors =>
              BadRequest("WRONG!" + errors)
            }
          )
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