package controllers.api.datasets

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString
import controllers.portal.base.PortalController
import controllers.{AppComponents, DataFormat}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.{ContentTypes, HeaderNames}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.cypher.{CypherQueryService, Neo4jCypherService, CypherResult}
import services.data.ItemNotFound
import utils.PageParams

import scala.util.Failure


@Singleton
case class Datasets @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  cypher: Neo4jCypherService,
  cypherQueries: CypherQueryService)(
  implicit mat: Materializer
) extends PortalController {

  private val logger = Logger(Datasets.getClass)

  def list(): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    cypherQueries.list(PageParams.empty.withoutLimit, Map("public" -> "true")).map { queries =>
      Ok(views.html.api.datasets.datasets(queries))
    }
  }

  def run(id: String, format: DataFormat.Value): Action[AnyContent] = Action.async { implicit request =>
    cypherQueries.get(id).map { query =>
      if (!query.public) throw new ItemNotFound(id) else {
        val name = query.name.replaceAll("[\\W-]", "-").toLowerCase
        val filename = s"$name-$id.$format"
        val ctHeader = HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=$filename"
        format match {
          case DataFormat.Csv | DataFormat.Tsv =>
            import com.fasterxml.jackson.dataformat.csv.CsvSchema
            import utils.CsvHelpers
            val sep: Char = if (format == DataFormat.Csv) ',' else '\t'
            val csvFormat = CsvSchema.builder().setColumnSeparator(sep).setUseHeader(false)
            val writer = CsvHelpers.mapper.writer(csvFormat.build())
              val csvRows: Source[ByteString, _] = cypher.rows(query.query).map { row =>
                val cols: Seq[String] = row.collect(CypherResult.jsToString)
                ByteString.fromArray(writer.writeValueAsBytes(cols.toArray))
              }.watchTermination()(Keep.right).mapMaterializedValue { f =>
                f.onComplete {
                  case Failure(e) => logger.error(s"Error generating $format: ", e)
                  case _ =>
                }
                f
              }
              Ok.chunked(csvRows)
                .as(s"text/$format; charset=utf-8")
                .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=$filename")
          case DataFormat.Json =>
              Ok.chunked(cypher.legacy(query.query))
                .as(ContentTypes.JSON)
                .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=$filename")
          case _ => NotAcceptable(s"Unsupported type: $format")
        }
      }
    }
  }
}
