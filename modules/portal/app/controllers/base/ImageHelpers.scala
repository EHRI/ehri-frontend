package controllers.base

import models.Model
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.tasks.UnsupportedFormatException
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{MultipartFormData, RequestHeader}
import services.storage.FileStorage
import utils.ImageSniffer

import java.io.File
import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

case class UnrecognizedType() extends Exception

case class ResolutionTooHigh() extends Exception

trait ImageHelpers {

  protected def config: play.api.Configuration

  protected def fileStorage: FileStorage

  private def validateImageFile(file: File, name: String, contentType: Option[String]): Unit = try {
    val pixels = ImageSniffer.getTotalPixels(file, name)
    if (!contentType.exists(_.startsWith("image/")))
      throw UnrecognizedType()
    else if (pixels > config.get[Long]("ehri.portal.profile.maxImagePixels"))
      throw ResolutionTooHigh()
  } catch {
    case _: ImageSniffer.UnsupportedImageTypeException => throw UnrecognizedType()
  }

  /**
    * Given an image file, convert it to a thumbnail of the size specified
    * in config and upload it to file storage, returning the URI.
    *
    * @param fileOpt an uploaded file
    * @param item    an entity represented by the image
    * @return the URI of the uploaded thumbnail
    * @throws UnrecognizedType  if the file is not an image
    * @throws ResolutionTooHigh if the image dimensions are too large
    */
  protected def convertAndUploadFile(fileOpt: Option[MultipartFormData.FilePart[TemporaryFile]], item: Model)(implicit request: RequestHeader, ec: ExecutionContext): Future[Option[URI]] = {
    fileOpt match {
      case Some(file) => try {
        validateImageFile(file.ref.toFile, file.filename, file.contentType)
        val instance = config.getOptional[String]("storage.instance").getOrElse(request.host)
        val stamp = FileStorage.fingerprint(file.ref.toFile)
        val extension = file.filename.substring(file.filename.lastIndexOf(".")).toLowerCase
        val storeName = s"$instance/images/${item.isA}/${item.id}_$stamp$extension"
        val temp = File.createTempFile(item.id, extension)
        val width = config.get[Int]("ehri.portal.profile.thumbWidth")
        val height = config.get[Int]("ehri.portal.profile.thumbHeight")
        Thumbnails.of(file.ref.toFile).size(width, height).toFile(temp)
        val url: Future[URI] = fileStorage.putFile(storeName, temp, public = true)
        url.onComplete { _ => temp.delete() }
        url.map(Some(_))
      } catch {
        case _: UnsupportedFormatException => Future.failed(UnrecognizedType())
        case e: Throwable => Future.failed(e)
      }
      case None => Future.successful(Option.empty[URI])
    }
  }
}
