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
    case _: UnsupportedOperationException => throw UnrecognizedType()
  }

  private def isSvg(file: MultipartFormData.FilePart[TemporaryFile]): Boolean =
    file.contentType.contains("image/svg+xml") || file.filename.toLowerCase.endsWith(".svg")

  /**
    * Given an image file, convert it to a thumbnail of the size specified
    * in config and upload it to file storage, returning the URI.
    *
    * SVG images, if allowed, are uploaded as-is, since they need no resizing.
    * NB: they are not sanitised, so should only be accepted from trusted users
    * and displayed via `img` elements (in which their scripts do not run.)
    *
    * @param fileOpt  an uploaded file
    * @param item     an entity represented by the image
    * @param allowSvg whether to accept SVG images
    * @return the URI of the uploaded thumbnail
    * @throws UnrecognizedType  if the file is not an image
    * @throws ResolutionTooHigh if the image dimensions are too large
    */
  protected def convertAndUploadFile(fileOpt: Option[MultipartFormData.FilePart[TemporaryFile]], item: Model, allowSvg: Boolean = false)(implicit request: RequestHeader, ec: ExecutionContext): Future[Option[URI]] = {
    def storeName(file: File, extension: String): String = {
      val instance = config.getOptional[String]("storage.instance").getOrElse(request.host)
      s"$instance/images/${item.isA}/${item.id}_${FileStorage.fingerprint(file)}$extension"
    }

    fileOpt match {
      case Some(file) if allowSvg && isSvg(file) =>
        fileStorage.putFile(storeName(file.ref.toFile, ".svg"), file.ref.toFile,
          contentType = Some("image/svg+xml"), public = true).map(Some(_))
      case Some(file) => try {
        validateImageFile(file.ref.toFile, file.filename, file.contentType)
        val extension = file.filename.substring(file.filename.lastIndexOf(".")).toLowerCase
        val temp = File.createTempFile(item.id, extension)
        val width = config.get[Int]("ehri.portal.profile.thumbWidth")
        val height = config.get[Int]("ehri.portal.profile.thumbHeight")
        Thumbnails.of(file.ref.toFile).size(width, height).toFile(temp)
        val url: Future[URI] = fileStorage.putFile(storeName(file.ref.toFile, extension), temp, public = true)
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
