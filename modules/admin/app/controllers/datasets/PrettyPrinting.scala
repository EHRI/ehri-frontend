package controllers.datasets

import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import models._
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.connectors.xml.scaladsl.{XmlParsing, XmlWriting}
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.util.ByteString
import play.api.http.ContentTypes
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc._
import services.data.DataHelpers
import services.ingest._
import services.storage.FileStorage

import javax.inject._
import scala.concurrent.ExecutionContext


@Singleton
case class PrettyPrinting @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  @Named("dam") storage: FileStorage,
)(implicit mat: Materializer, executionContext: ExecutionContext) extends AdminController with ApiBodyParsers with StorageHelpers {

  private val xmlFormatFlow: Flow[ByteString, ByteString, NotUsed] = XmlParsing.parser()
    .via(XmlFormatter.format)
    .via(XmlWriting.writer)
    .filter(_.nonEmpty) // Necessary to prevent issues with HTTP chunks

  private val prettyPrintJsonFlow: Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString]
      .fold(ByteString.empty)(_ ++ _)
      .map { bytes =>
        val pretty = Json.prettyPrint(Json.parse(bytes.utf8String))
        ByteString(pretty)
      }

  private def xmlFormatBodyParser: BodyParser[Source[ByteString, _]] = BodyParser { _ =>
    Accumulator.source[ByteString]
      .map(_.via(xmlFormatFlow))
      .map(Right.apply)
  }

  private def prettyJsonBodyParser: BodyParser[Source[ByteString, _]] = BodyParser { _ =>
    Accumulator.source[ByteString]
      .map(_.via(prettyPrintJsonFlow))
      .map(Right.apply)
  }

  private def reformatData(data: Source[ByteString, _], contentType: Option[String]): Source[ByteString, _] = {
    contentType match {
      case Some(ct) if ct.equalsIgnoreCase(ContentTypes.JSON) =>
        data.via(prettyPrintJsonFlow)
      case Some(ct) if ct.toLowerCase.contains("/xml") =>
        data.via(xmlFormatFlow)
      case _ => data
    }
  }

  private def combinedBodyParser(
    jsonParser: BodyParser[Source[ByteString, _]],
    xmlParser:  BodyParser[Source[ByteString, _]]
  ): BodyParser[Source[ByteString, _]] =
    BodyParser { requestHeader =>
      requestHeader.contentType match {
        case Some(ct) if ct.equalsIgnoreCase(ContentTypes.JSON) => jsonParser(requestHeader)
        case Some(ct) if ct.contains("/xml")  =>
          xmlParser(requestHeader)
        // Otherwise, just return the original data
        case _ => Accumulator.source[ByteString]
          .map(Right.apply)
      }
    }

  def reformat(id: String, ds: String, stage: FileStage.Value, fileName: String, versionId: Option[String]): Action[AnyContent] = Action.async { implicit req =>
    storage.get(s"${prefix(id, ds, stage)}$fileName", versionId = versionId).map {
      case Some((meta, bytes)) =>
        val newBytes = reformatData(bytes, meta.contentType)
        Ok.chunked(newBytes).as(meta.contentType.getOrElse(ContentTypes.BINARY))
      case _ => NotFound
    }
  }

  def reformatPost(id: String): Action[Source[ByteString, _]] = Action.async(combinedBodyParser(prettyJsonBodyParser, xmlFormatBodyParser)) { request =>
    request.body
      .runWith(Sink.fold(ByteString.empty)(_ ++ _))
      .map(bytes => Ok(bytes.utf8String))
  }
}
