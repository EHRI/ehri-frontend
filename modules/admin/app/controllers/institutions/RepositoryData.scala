package controllers.institutions

import java.net.URI

import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import javax.inject._
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{Format, JsArray, Json}
import play.api.mvc._
import services.data.DataHelpers
import services.ingest.EadValidator
import services.search._
import services.storage.{DOFileStorage, FileStorage}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

case class FileToUpload(name: String, `type`: String, size: Long)
object FileToUpload {
  implicit val _json: Format[FileToUpload] = Json.format[FileToUpload]
}

@Singleton
case class RepositoryData @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  searchIndexer: SearchIndexMediator,
  fileStorage: FileStorage,
  eadValidator: EadValidator
)(
  implicit mat: Materializer
) extends AdminController
  with Read[Repository]
  with Update[Repository] {

  private val repositoryDataRoutes = controllers.institutions.routes.RepositoryData

  private val fileForm = Form(single("file" -> text))
  private val storage = DOFileStorage(config)(mat.system, mat)
  private val bucket = "ehri-assets"
  private val prefix: String => String = id => s"ingest/$id/"

  def manager(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.repository.data.manager(request.item))
  }

  def validateEad(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.repository.validateEad(Map.empty[String, Seq[EadValidator#Error]], request.item, fileForm,
      repositoryDataRoutes.validateEadPost(id)))
  }

  def validateEadPost(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    request.body.asMultipartFormData.map { data =>
      val results: Seq[Future[(String, Seq[EadValidator#Error])]] = data.files.map { file =>
        eadValidator.validateEad(file.ref.toPath).map(errs => file.filename -> errs)
      }

      Future.sequence(results).map { out =>
        Ok(views.html.admin.repository.validateEad(out.sortBy(_._1).toMap, request.item, fileForm,
          repositoryDataRoutes.validateEadPost(id)))
      }
    }.getOrElse {
      immediate(Redirect(repositoryDataRoutes.validateEad(id)))
    }
  }

  def validateEadFromStorage(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.listFiles(bucket, prefix = Some(prefix(id))).runWith(Sink.seq).flatMap  { files =>
      val results: Seq[Future[(String, Seq[EadValidator#Error])]] = files.map { file =>
        eadValidator.validateEad(Uri(storage.uri(bucket, file.key).toString))
          .map(errs => file.key.replace(prefix(id), "") -> errs)
      }

      Future.sequence(results).map { out =>
        Ok(views.html.admin.repository.validateEad(out.sortBy(_._1).toMap, request.item, fileForm,
          repositoryDataRoutes.validateEadPost(id)))
      }
    }
  }

  def listFiles(id: String, path: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.listFiles(bucket, prefix = Some(prefix(id + path))).runWith(Sink.seq).map { files =>
      Ok(Json.toJson(files.map(f => f.copy(key = f.key.replace(prefix(id), "")))))
    }
  }

  def deleteFiles(id: String): Action[Seq[String]] = EditAction(id).async(parse.json[Seq[String]]) { implicit request =>
    val keys = request.body.map(path => s"${prefix(id)}$path")
    storage.deleteFiles(bucket, keys: _*).map { deleted =>
      Ok(Json.toJson(deleted.map(_.replace(prefix(id), ""))))
    }
  }

  def uploadHandle(id: String): Action[FileToUpload] = EditAction(id).apply(parse.json[FileToUpload]) { implicit request =>
    val path = s"${prefix(id)}${request.body.name}"
    val url = storage.uri(bucket, path, contentType = Some(request.body.`type`))
    Ok(Json.obj("presignedUrl" -> url))
  }

  def uploadData(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.listFiles(bucket, prefix = Some(prefix(id))).runWith(Sink.seq).map  { files =>
      val stripPrefix = files.map(f => f.copy(key = f.key.replaceFirst(prefix(id), "") ))
      Ok(views.html.admin.repository.uploadData(request.item, stripPrefix, fileForm,
        repositoryDataRoutes.uploadDataPost(id)))
    }
  }

  def uploadDataDirect(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.listFiles(bucket, prefix = Some(prefix(id))).runWith(Sink.seq).map  { files =>
      val stripPrefix = files.map(f => f.copy(key = f.key.replaceFirst(prefix(id), "") ))
      if (isAjax) Ok(views.html.admin.repository.uploadDataList(request.item, stripPrefix))
      else Ok(views.html.admin.repository.uploadData(request.item, stripPrefix, fileForm,
        repositoryDataRoutes.uploadDataDirect(id)))
    }
  }

  def uploadDataDirectPost(id: String, fileName: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    val path = s"${prefix(id)}$fileName"
    val uri = storage.uri(bucket, path, contentType = request.contentType)
    Ok(Json.obj("uri" -> uri))
  }

  def deleteDataPost(id: String, fileName: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.deleteFiles(bucket, s"${prefix(id)}$fileName").map { r =>
      Ok(Json.obj("ok" -> true))
    }
  }

  def uploadDataPost(id: String): Action[AnyContent] = EditAction(id).async(parse.anyContent(Some(parse.UNLIMITED))) { implicit request =>
    request.body.asMultipartFormData.map { data =>

      val uris: Seq[Future[URI]] = data.files.map { file =>
        val path = s"${prefix(id)}${file.filename}"
        storage.putFile(bucket, path, file.ref.path.toFile)
      }
      Future.sequence(uris).map { _ =>
        Redirect(repositoryDataRoutes.uploadData(id))
          .flashing("success" -> "That worked!")
      }
    }.getOrElse {
      immediate(Redirect(repositoryDataRoutes.uploadData(id)))
    }
  }
}