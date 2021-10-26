package controllers.cypher


import akka.stream.alpakka.csv.scaladsl.CsvFormatting

import javax.inject.{Inject, Singleton}
import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString
import controllers.{AppComponents, DataFormat}
import controllers.base.AdminController
import models.CypherQuery
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.http._
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.cypher.{CypherExplain, CypherQueryService, CypherResult, WsCypherService}
import services.search.SearchParams
import utils.PageParams

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.util.Failure


@Singleton
case class CypherQueries @Inject()(
                                    controllerComponents: ControllerComponents,
                                    appComponents: AppComponents,
                                    cypher: WsCypherService,
                                    cypherQueries: CypherQueryService
) extends AdminController {

  private val logger = Logger(CypherQueries.getClass)

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

  def cypherQuery: Action[AnyContent] = AdminAction.apply { implicit request =>
    queryForm.bindFromRequest().fold(
      err => BadRequest(err.errorsAsJson),
      q => Ok.chunked(cypher.legacy(q, Map.empty))
    )
  }

  def listQueries(q: Option[String], sort: Option[String], public: Option[Boolean],
        paging: PageParams): Action[AnyContent] = WithUserAction.async { implicit request =>
    val params = Seq(SearchParams.QUERY -> q, SearchParams.SORT -> sort, "public" -> public.map(_.toString))
      .collect { case (k, Some(v)) => k -> v}
      .toMap
    cypherQueries.list(paging, params).map { queries =>
      Ok(views.html.admin.cypherQueries.list(queries, q, sort,
        controllers.cypher.routes.CypherQueries.listQueries()))
    }
  }

  def createQuery: Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.admin.cypherQueries.form(None, CypherQuery.form,
      controllers.cypher.routes.CypherQueries.createQueryPost()))
  }

  def createQueryPost: Action[AnyContent] = AdminAction.async { implicit request =>
    CypherQuery.form.bindFromRequest().fold(
      errors => immediate(BadRequest(views.html.admin.cypherQueries.form(None, errors,
        controllers.cypher.routes.CypherQueries.createQueryPost()))),
      queryModel => cypherQueries.create(queryModel.copy(userId = Some(request.user.id))).map { _ =>
        Redirect(controllers.cypher.routes.CypherQueries.listQueries())
          .flashing("success" -> "item.create.confirmation")
      }
    )
  }

  def updateQuery(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    cypherQueries.get(id).map { query =>
      val f = CypherQuery.form.fill(query)
      Ok(views.html.admin.cypherQueries.form(Some(query), f,
        controllers.cypher.routes.CypherQueries.updateQueryPost(id)))
    }
  }

  def updateQueryPost(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    CypherQuery.form.bindFromRequest().fold(
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

  def deleteQuery(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    cypherQueries.get(id).map { query =>
      Ok(views.html.admin.cypherQueries.delete(query,
        controllers.cypher.routes.CypherQueries.deleteQueryPost(id),
        controllers.cypher.routes.CypherQueries.listQueries()))
    }
  }

  def deleteQueryPost(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    cypherQueries.delete(id).map { _ =>
      Redirect(controllers.cypher.routes.CypherQueries.listQueries())
        .flashing("success" -> "item.delete.confirmation")
    }
  }

  def checkQueries(): Action[AnyContent] = AdminAction.async { implicit request =>
    cypherQueries.list(PageParams.empty.withoutLimit).flatMap { page =>
      val seq: Seq[Future[(CypherQuery, CypherExplain)]] = page.items.map { q =>
        cypher.explain(q.query, Map.empty).map(exp => q -> exp)
      }
      Future.sequence(seq).map { results =>
        Ok(views.html.admin.cypherQueries.check(results))
      }
    }
  }

  def executeQuery(id: String, format: DataFormat.Value): Action[AnyContent] = WithUserAction.async { implicit request =>
    cypherQueries.get(id).flatMap { query =>
      val name = query.name.replaceAll("[\\W-]", "-").toLowerCase
      val filename = s"$name-$id.$format"
      format match {
        case DataFormat.Csv | DataFormat.Tsv =>
          val sep: Char = if (format == DataFormat.Csv) ',' else '\t'
          val csvFormat = CsvFormatting.format(delimiter = sep)
          val csvRows: Source[ByteString, _] = cypher.rows(query.query)
              .map(_.collect(CypherResult.jsToString))
              .via(csvFormat)
              .watchTermination()(Keep.right).mapMaterializedValue { f =>
            f.onComplete {
              case Failure(e) => logger.error(s"Error generating $format: ", e)
              case _ =>
            }
            f
          }
          immediate(Ok.chunked(csvRows)
            .as(s"text/$format; charset=utf-8")
            .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=$filename"))
        case DataFormat.Html =>
          cypher.get(query.query).map { rows =>
            Ok(views.html.admin.cypherQueries.results(query, rows))
          }
        case DataFormat.Json =>
          immediate(Ok.chunked(cypher.legacy(query.query)).as(ContentTypes.JSON)
              .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=$filename"))
        case _ => immediate(NotAcceptable(s"Unsupported type: $format"))
      }
    }
  }
}
