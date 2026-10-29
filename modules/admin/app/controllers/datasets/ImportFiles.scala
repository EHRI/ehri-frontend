package controllers.datasets

import eu.ehri.project.xml.XQueryTabularHeaderReader
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import controllers.generic._
import models._
import play.api.cache.AsyncCacheApi
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.json.{Format, JsArray, JsObject, Json, Writes}
import play.api.libs.streams.Accumulator
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.data.DataHelpers
import services.datasets.ImportDatasetService
import services.ingest._
import services.storage.FileStorage
import services.transformation.utils.getUtf8Transcoder

import java.net.URI
import javax.inject._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal


case class FileToUpload(name: String, `type`: String, size: Long, meta: Map[String, String] = Map.empty)

object FileToUpload {
  implicit val _json: Format[FileToUpload] = Json.format[FileToUpload]
}

case class ValidationResult(key: String, eTag: Option[String], errors: Seq[FileValidationError])

object ValidationResult {
  implicit val _json: Writes[ValidationResult] = Json.writes[ValidationResult]
}

@Singleton
case class ImportFiles @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  @Named("dam") storage: FileStorage,
  eadValidator: EadValidator,
  fieldValidator: FieldValidator,
  headerReader: XQueryTabularHeaderReader,
  ingestService: IngestService,
  importLogService: ImportLogService,
  asyncCache: AsyncCacheApi,
  datasets: ImportDatasetService,
  ws: WSClient
)(implicit mat: Materializer, executionContext: ExecutionContext) extends AdminController with ApiBodyParsers with StorageHelpers with Update[Repository] {

  def listFiles(id: String, ds: String, stage: FileStage.Value, path: Option[String], from: Option[String]): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.listFiles(prefix = Some(prefix(id, ds, stage) + path.getOrElse("")),
      from.map(key => s"${prefix(id, ds, stage)}$key"), max = 50).map { list =>
      Ok(Json.toJson(list.copy(files = list.files.map(f => f.copy(key = f.key.replace(prefix(id, ds, stage), ""))))))
    }
  }

  def countFiles(id: String, ds: String, stage: FileStage.Value, path: Option[String]): Action[AnyContent] = EditAction(id).async { implicit request =>
    import scala.concurrent.duration._

    val pathPrefix: String = prefix(id, ds, stage) + path.getOrElse("")
    asyncCache.getOrElseUpdate(s"bucket:count:${storage.name}/$pathPrefix", 1.minute) {
      storage.count(Some(prefix(id, ds, stage) + path.getOrElse(""))).map { count =>
        Json.obj("path" -> pathPrefix, "count" -> count)
      }
    }.map(Ok(_))
  }

  def fileUrls(id: String, ds: String, stage: FileStage.Value): Action[Seq[String]] = EditAction(id).apply(apiJson[Seq[String]]) { implicit request =>
    val keys = request.body.map(path => s"${prefix(id, ds, stage)}$path")
    val result: Map[String, URI] = keys.map(key => key.replace(prefix(id, ds, stage), "") -> storage.uri(key)).toMap
    Ok(Json.toJson(result))
  }

  def copyFile(id: String, ds: String, stage: FileStage.Value, fileName: String, toDs: String, toName: Option[String], versionId: Option[String]): Action[AnyContent] = EditAction(id).async { implicit request =>
    val fromPrefix = prefix(id, ds, stage)
    val toPrefix = prefix(id, toDs, stage)
    val toFileName = toName.getOrElse(fileName)
    val fromPath = fromPrefix + fileName
    val toPath = toPrefix + toFileName

    storage.info(fromPath, versionId).flatMap {
      case Some((srcMeta, _)) =>
        storage.info(toPath).flatMap {
          case Some((dstMeta, _)) =>
            if (srcMeta.eTag.isDefined && srcMeta.eTag == dstMeta.eTag) {
              // Files are the same, no need to copy
              Future.successful(Ok(Json.obj(
                "message" -> "File with identical contents already exists",
                "uri" -> storage.uri(dstMeta.key, contentType = dstMeta.contentType, duration = 2.hours))))
            } else {
              // Copy the file...
              storage.copyFile(fromPath, toPath)
                .map(uri => Ok(Json.obj("message" -> "File updated", "uri" -> uri)))
            }
          case _ =>
            storage.copyFile(fromPath, toPath)
              .map(uri => Created(Json.obj("message" -> "File copied", "uri" -> uri)))
        }

      case _ => Future.successful(NotFound)
    }
  }

  // NB: this must not consume the body itself, since body parsers run
  // before the action's permission checks. The body is only streamed to
  // storage once the request has been authorised.
  private def streamBody: BodyParser[Source[ByteString, _]] = BodyParser { _ =>
    Accumulator.source[ByteString].map(Right.apply)
  }

  def uploadStream(id: String, ds: String, stage: FileStage.Value, fileName: String): Action[Source[ByteString, _]] =
    EditAction(id).async(streamBody) { implicit request =>
      // Upload via the server. Normally you'd PUT direct from the client
      // to the storage, but this is useful for testing
      storage.putBytes(s"${prefix(id, ds, stage)}$fileName", request.body, request.contentType).map { url =>
        Ok(Json.prettyPrint(Json.obj("url" -> url.toString))).as(ContentTypes.JSON)
      }
    }

  def download(id: String, ds: String, stage: FileStage.Value, fileName: String, versionId: Option[String]): Action[AnyContent] = EditAction(id).async { implicit req =>
    storage.get(s"${prefix(id, ds, stage)}$fileName", versionId = versionId).map {
      case Some((meta, bytes)) =>
        Ok.chunked(bytes)
          .as(meta.contentType.getOrElse(ContentTypes.BINARY))
          .withHeaders(meta.eTag.toSeq.map(tag => HeaderNames.ETAG -> tag): _*)
          .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment;filename=$fileName")
      case _ => NotFound
    }
  }

  def info(id: String, ds: String, stage: FileStage.Value, fileName: String, versionId: Option[String]): Action[AnyContent] = EditAction(id).async { implicit req =>
    val pre = prefix(id, ds, stage)
    val path = pre + fileName
    for {
      Some((meta, userMeta)) <- storage.info(path, versionId)
      versions <- storage.listVersions(path)
    } yield Ok(Json.obj(
      "meta" -> meta.copy(key = meta.key.replace(pre, "")),
      "user" -> userMeta,
      "presignedUrl" -> storage.uri(path, 2.hours, versionId = versionId, contentType = meta.contentType),
      "versions" -> versions.files.map(v => v.copy(key = v.key.replace(pre, "")))
    ))
  }

  def uploadHandle(id: String, ds: String, stage: FileStage.Value): Action[FileToUpload] = EditAction(id).apply(apiJson[FileToUpload]) { implicit request =>
    val path = s"${prefix(id, ds, stage)}${request.body.name}"
    val url = storage.uri(path, contentType = Some(request.body.`type`), meta = request.body.meta)
    Ok(Json.obj("presignedUrl" -> url))
  }

  def deleteFiles(id: String, ds: String, stage: FileStage.Value): Action[Seq[String]] = EditAction(id).async(apiJson[Seq[String]]) { implicit request =>
    def deleteBatch(batch: Seq[String]): Future[Int] = storage.deleteFiles(batch: _*).map(_.size)

    val pre = prefix(id, ds, stage)
    asyncCache.remove(s"bucket:count:${storage.name}/$pre")

    val src: Source[String, _] = if (request.body.isEmpty)
      storage.streamFiles(Some(pre)).map(f => f.key)
    else Source(request.body.map(p => pre + p).toList)

    src
      .grouped(200)
      .mapAsync(2)(deleteBatch)
      .runWith(Sink.fold(0)(_ + _))
      .map ( num => Ok(Json.obj("deleted" -> num)))
  }

  // Report file-level problems as data, not a failed Future, so one bad file
  // doesn't fail the whole batch.
  private def fileError(msg: String): Seq[FileValidationError] = Seq(FieldValidationError("(file)", "error", msg))

  // Read a file as text. As when converting, the dataset's content type (which
  // may specify a charset) takes precedence over the stored file's.
  private def readText(key: String, contentType: Option[String]): Future[Option[String]] =
    storage.get(key).flatMap {
      case Some((meta, src)) =>
        val transcoder = getUtf8Transcoder(contentType.orElse(meta.contentType))
        val utf8src = transcoder.map(tc => src.via(tc)).getOrElse(src)
        utf8src.runFold(ByteString.empty)(_ ++ _).map(bytes => Some(bytes.utf8String))
      case None => Future.successful(None)
    }

  private def validateTabularFile(key: String, separator: String, contentType: Option[String]): Future[Seq[FileValidationError]] =
    readText(key, contentType).map {
      case Some(text) =>
        try {
          val headerJson = headerReader.readHeader(text, Map("input.separator" -> separator))
          val header = Json.parse(headerJson).as[Seq[String]]
          fieldValidator.validateFields(header)
        } catch {
          case NonFatal(e) => fileError(s"Unable to read header row: ${e.getMessage}")
        }
      case None => fileError("File not found")
    }

  // A JSON file is an object, or an array of objects, whose keys are the same
  // field names as the header of a CSV file. For an array, identical errors in
  // several objects are reported once, with the (1-based) numbers of the records.
  private def validateJsonFile(key: String, contentType: Option[String]): Future[Seq[FileValidationError]] = {
    val expected = "Expected a JSON object or an array of objects"

    def withRecords(error: FieldValidationError, records: Seq[Int]): FieldValidationError = {
      val shown = records.take(5).mkString(", ")
      val more = if (records.size > 5) s" and ${records.size - 5} more" else ""
      error.copy(error = s"${error.error} (record${if (records.size > 1) "s" else ""} $shown$more)")
    }

    readText(key, contentType).map {
      case Some(text) =>
        try Json.parse(text) match {
          case obj: JsObject => fieldValidator.validateFields(obj.keys.toSeq)
          case JsArray(items) if items.forall(_.isInstanceOf[JsObject]) =>
            val errors = items.zipWithIndex.flatMap { case (obj, i) =>
              fieldValidator.validateFields(obj.as[JsObject].keys.toSeq).map(_ -> (i + 1))
            }
            errors.map(_._1).distinct.map(e => withRecords(e, errors.collect { case (`e`, n) => n }.toSeq)).toSeq
          case _ => fileError(expected)
        } catch {
          case NonFatal(e) => fileError(s"Invalid JSON: ${e.getMessage}")
        }
      case None => fileError("File not found")
    }
  }

  def validateFiles(id: String, ds: String, stage: FileStage.Value): Action[Map[String, String]] = WithItemPermissionAction(id, PermissionType.Update).async(apiJson[Map[String, String]]) { implicit request =>
    // Input is a map of eTag -> key
    // Convert the map to a sequence and reverse key/value
    val pre = prefix(id, ds, stage)
    val src: Source[(String, Option[String]), _] = if (request.body.isEmpty) storage
        .streamFiles(Some(pre))
        .map(f => f.key -> f.eTag)
    else Source(request.body.map{ case (t, k) => (pre + k) -> Some(t)}.toList)

    // Validate according to the dataset's media type, as used to choose how
    // its data is ingested. Datasets without one are treated as EAD/XML.
    datasets.find(id, ds).flatMap { datasetOpt =>
      val validate: String => Future[Seq[FileValidationError]] = datasetOpt.flatMap(_.mediaType) match {
        case Some("text/csv") => validateTabularFile(_, "comma", datasetOpt.flatMap(_.contentType))
        case Some("application/json") => validateJsonFile(_, datasetOpt.flatMap(_.contentType))
        case _ => key => eadValidator.validateEad(Uri(storage.uri(key).toString))
      }
      val results: Future[Seq[ValidationResult]] = src.mapAsync(3) { case (key, tag) =>
        validate(key).map(errs => ValidationResult(key.replace(pre, ""), tag, errs))
      }.runWith(Sink.seq)
      results.map(out => Ok(Json.toJson(out)))
    }.recover {
      case e => BadRequest(Json.obj("error" -> e.getMessage))
    }
  }
}
