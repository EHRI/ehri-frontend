package controllers.cypher

import javax.inject.{Inject, Singleton}

import auth.AccountManager
import controllers.DataFormat
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.concurrent.Execution.Implicits._
import backend.rest.cypher.CypherDAO
import backend.{Backend, CypherQueryDAO}
import controllers.base.AdminController
import models.{ResultFormat, CypherQuery}
import play.api.cache.CacheApi
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import views.MarkdownRenderer
import scala.concurrent.Future.{successful => immediate}

@Singleton
case class CypherQueries @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: utils.MovedPageLookup,
  messagesApi: MessagesApi,
  cypher: CypherDAO,
  cypherQueryDAO: CypherQueryDAO,
  md: MarkdownRenderer
) extends AdminController {

  private val queryForm = Form(single("q" -> nonEmptyText))

  private val defaultCypher =
    """
      |MATCH (n:UserProfile)
      |RETURN n, n.name
      |LIMIT 100
    """.stripMargin

  def cypherForm = AdminAction { implicit request =>
    Ok(views.html.admin.queryForm(queryForm.fill(defaultCypher),
      controllers.cypher.routes.CypherQueries.cypherQuery(), "Cypher"))
  }

  def cypherQuery = AdminAction.async { implicit request =>
    // NB: JS doesn't handle streaming responses well, so if we're
    // calling it from there don't chunk the response.
    val q: String = queryForm.bindFromRequest.value.getOrElse("")
    if (isAjax) cypher.cypher(q, Map.empty).map(r => Ok(r))
    else cypher.stream(q, Map.empty).map { case (headers, stream) =>
      Status(headers.status).chunked(stream)
    }
  }

  def listQueries = WithUserAction.async { implicit request =>
    cypherQueryDAO.list().map { queries =>
      Ok(views.html.admin.cypherQueries.list(queries))
    }
  }

  def createQuery = AdminAction { implicit request =>
    Ok(views.html.admin.cypherQueries.create(CypherQuery.form,
      controllers.cypher.routes.CypherQueries.createQueryPost()))
  }

  def createQueryPost = AdminAction.async { implicit request =>
    CypherQuery.form.bindFromRequest.fold(
      errors => immediate(BadRequest(views.html.admin.cypherQueries.create(errors,
        controllers.cypher.routes.CypherQueries.createQueryPost()))),
      queryModel => {
        cypherQueryDAO.create(queryModel).map { _ =>
          Redirect(controllers.cypher.routes.CypherQueries.listQueries())
            .flashing("success" -> "item.create.confirmation")
        }
      }
    )
  }

  def updateQuery(id: String) = AdminAction.async { implicit request =>
    cypherQueryDAO.get(id).map { query =>
      val f = CypherQuery.form.fill(query)
      Ok(views.html.admin.cypherQueries.edit(query, f,
        controllers.cypher.routes.CypherQueries.updateQueryPost(id)))
    }
  }

  def updateQueryPost(id: String) = AdminAction.async { implicit request =>
    CypherQuery.form.bindFromRequest.fold(
      errors => cypherQueryDAO.get(id).map { query =>
        BadRequest(views.html.admin.cypherQueries.edit(query, errors,
        controllers.cypher.routes.CypherQueries.updateQueryPost(id)))
      },
      queryModel => {
        cypherQueryDAO.update(id, queryModel).map { _ =>
          Redirect(controllers.cypher.routes.CypherQueries.listQueries())
            .flashing("success" -> "item.update.confirmation")
        }
      }
    )
  }

  def deleteQuery(id: String) = AdminAction.async { implicit request =>
    cypherQueryDAO.get(id).map { query =>
      Ok(views.html.admin.cypherQueries.delete(query,
        controllers.cypher.routes.CypherQueries.deleteQueryPost(id),
        controllers.cypher.routes.CypherQueries.listQueries()))
    }
  }

  def deleteQueryPost(id: String) = AdminAction.async { implicit request =>
    cypherQueryDAO.delete(id).map { _ =>
      Redirect(controllers.cypher.routes.CypherQueries.listQueries())
        .flashing("success" -> "item.delete.confirmation")
    }
  }

  def executeQuery(id: String, format: DataFormat.Value) = WithUserAction.async { implicit request =>
    cypherQueryDAO.get(id).flatMap { query =>
      val name = query.name.replaceAll("[\\W-]", "-").toLowerCase
      val filename = s"$name-$id.$format"
      format match {
        case DataFormat.Csv | DataFormat.Tsv =>
          cypher.get[ResultFormat](query.query, Map.empty).map { r =>
            Ok(r.toCsv(sep = if (format == DataFormat.Csv) ',' else '\t', quote = false))
              .as(s"text/$format; charset=utf-8")
              .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename='$filename'")
          }
        case DataFormat.Json =>
          cypher.stream(query.query).map { case (head, body) =>
            Ok.stream(body).as(ContentTypes.JSON)
              .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename='$filename'")
          }
        case _ => immediate(NotAcceptable(s"Unsupported type: $format"))
      }
    }
  }
}
