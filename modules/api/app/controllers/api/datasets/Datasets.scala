package controllers.api.datasets

import akka.stream.Materializer
import akka.stream.alpakka.csv.scaladsl.CsvFormatting
import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString
import controllers.portal.base.PortalController
import controllers.{AppComponents, DataFormat}
import javax.inject.{Inject, Singleton}
import models.CypherQuery
import play.api.Logger
import play.api.http.{ContentTypes, HeaderNames}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.cypher.{CypherQueryService, CypherResult, WsCypherService}
import services.data.ItemNotFound
import services.search.SearchParams
import utils.PageParams

import scala.util.Failure


@Singleton
case class Datasets @Inject()(
                               controllerComponents: ControllerComponents,
                               appComponents: AppComponents,
                               cypher: WsCypherService,
                               cypherQueries: CypherQueryService)(
  implicit mat: Materializer
) extends PortalController {

  private val logger = Logger(Datasets.getClass)

  import play.api.libs.json._
  private implicit val cypherQueryWrites: Writes[CypherQuery] = Writes { query =>
    Json.obj(
      "id" -> query.objectId,
      "name" -> query.name,
      "description" -> query.description,
      "created" -> query.created,
      "updated" -> query.updated
    )
  }

  def list(q: Option[String], sort: Option[String], paging: PageParams, format: DataFormat.Value): Action[AnyContent] =
    OptionalUserAction.async { implicit request =>
      val params = Seq(SearchParams.QUERY -> q, SearchParams.SORT -> sort, "public" -> Some(true.toString))
        .collect { case (k, Some(v)) => k -> v}
        .toMap
      cypherQueries.list(paging, params).map { queries =>
        format match {
          case DataFormat.Json => Ok(Json.toJson(queries))
          case _ => Ok(views.html.api.datasets.datasets(queries, q, sort,
            controllers.api.datasets.routes.Datasets.list(q, sort, paging, format)))
        }
      }
    }

  def get(id: String, format: DataFormat.Value): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    cypherQueries.get(id).map { query =>
      if (!query.public) throw new ItemNotFound(id) else {
        val name = query.name.replaceAll("[\\W-]", "-").toLowerCase
        val filename = s"$name-$id.$format"
        val ctHeader = HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=$filename"
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
            Ok.chunked(csvRows)
              .as(s"text/$format; charset=utf-8")
              .withHeaders(ctHeader)
          case DataFormat.Json =>
              Ok.chunked(cypher.legacy(query.query))
                .as(ContentTypes.JSON)
                .withHeaders(ctHeader)
          case _ => Ok(views.html.api.datasets.show(query))
        }
      }
    }
  }
}
