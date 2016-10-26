package controllers.cypher

import javax.inject.{Inject, Singleton}

import backend.CypherQueryService
import backend.rest.cypher.CypherService
import controllers.base.AdminController
import controllers.{Components, DataFormat}
import models.{CypherQuery, ResultFormat}
import play.api.data.Form
import play.api.data.Forms._
import play.api.http.{ContentTypes, HeaderNames}
import utils.PageParams

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class CypherQueries @Inject()(
  components: Components,
  cypher: CypherService,
  cypherQueries: CypherQueryService
) extends AdminController {

  private val queryForm = Form(
    single("q" -> nonEmptyText.verifying(CypherQuery.isReadOnly))
  )

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
    queryForm.bindFromRequest.fold(
      err => immediate(BadRequest(err.errorsAsJson)),
      q => cypher.stream(q, Map.empty).map { sr =>
        Status(sr.headers.status).chunked(sr.body)
      }
    )
  }

  def listQueries = WithUserAction.async { implicit request =>
    cypherQueries.list(PageParams.fromRequest(request)).map { queries =>
      Ok(views.html.admin.cypherQueries.list(queries))
    }
  }

  def createQuery = AdminAction { implicit request =>
    Ok(views.html.admin.cypherQueries.form(None, CypherQuery.form,
      controllers.cypher.routes.CypherQueries.createQueryPost()))
  }

  def createQueryPost = AdminAction.async { implicit request =>
    CypherQuery.form.bindFromRequest.fold(
      errors => immediate(BadRequest(views.html.admin.cypherQueries.form(None, errors,
        controllers.cypher.routes.CypherQueries.createQueryPost()))),
      queryModel => cypherQueries.create(queryModel.copy(userId = Some(request.user.id))).map { _ =>
        Redirect(controllers.cypher.routes.CypherQueries.listQueries())
          .flashing("success" -> "item.create.confirmation")
      }
    )
  }

  def updateQuery(id: String) = AdminAction.async { implicit request =>
    cypherQueries.get(id).map { query =>
      val f = CypherQuery.form.fill(query)
      Ok(views.html.admin.cypherQueries.form(Some(query), f,
        controllers.cypher.routes.CypherQueries.updateQueryPost(id)))
    }
  }

  def updateQueryPost(id: String) = AdminAction.async { implicit request =>
    CypherQuery.form.bindFromRequest.fold(
      errors => cypherQueries.get(id).map { query =>
        BadRequest(views.html.admin.cypherQueries.form(Some(query), errors,
        controllers.cypher.routes.CypherQueries.updateQueryPost(id)))
      },
      queryModel => cypherQueries.update(id, queryModel).map { _ =>
        Redirect(controllers.cypher.routes.CypherQueries.listQueries())
          .flashing("success" -> "item.update.confirmation")
      }
    )
  }

  def deleteQuery(id: String) = AdminAction.async { implicit request =>
    cypherQueries.get(id).map { query =>
      Ok(views.html.admin.cypherQueries.delete(query,
        controllers.cypher.routes.CypherQueries.deleteQueryPost(id),
        controllers.cypher.routes.CypherQueries.listQueries()))
    }
  }

  def deleteQueryPost(id: String) = AdminAction.async { implicit request =>
    cypherQueries.delete(id).map { _ =>
      Redirect(controllers.cypher.routes.CypherQueries.listQueries())
        .flashing("success" -> "item.delete.confirmation")
    }
  }

  def executeQuery(id: String, format: DataFormat.Value) = WithUserAction.async { implicit request =>
    cypherQueries.get(id).flatMap { query =>
      val name = query.name.replaceAll("[\\W-]", "-").toLowerCase
      val filename = s"$name-$id.$format"
      format match {
        case DataFormat.Csv | DataFormat.Tsv =>
          cypher.get[ResultFormat](query.query, Map.empty).map { r =>
            Ok(r.toCsv(sep = if (format == DataFormat.Csv) ',' else '\t', quote = false))
              .as(s"text/$format; charset=utf-8")
              .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename='$filename'")
          }
        case DataFormat.Html =>
          cypher.get[ResultFormat](query.query, Map.empty).map { r =>
            Ok(views.html.admin.cypherQueries.results(query, r))
          }
        case DataFormat.Json =>
          cypher.stream(query.query).map { sr =>
            Ok.chunked(sr.body).as(ContentTypes.JSON)
              .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename='$filename'")
          }
        case _ => immediate(NotAcceptable(s"Unsupported type: $format"))
      }
    }
  }
}
