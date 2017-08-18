package services.storage

import java.io.File
import java.net.URI
import javax.inject.Inject

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.alpakka.s3.acl.CannedAcl
import akka.stream.alpakka.s3.auth.AWSCredentials
import akka.stream.alpakka.s3.scaladsl.S3Client
import akka.stream.alpakka.s3.{MemoryBufferType, S3Settings}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

case class S3FileStorage @Inject()(config: play.api.Configuration)(implicit actorSystem: ActorSystem, mat: Materializer) extends FileStorage {

  private val logger = Logger(getClass)
  private implicit val ec: ExecutionContext = mat.executionContext
  private val s3config: AwsConfig = AwsConfig.fromConfig(config)
  private val cred = AWSCredentials(s3config.accessKey, s3config.secret)
  private val settings = new S3Settings(MemoryBufferType, "", None, cred, s3config.region, pathStyleAccess = false)
  println(s"Setting: $settings")
  private val client = new S3Client(settings)

  override def putBytes(classifier: String, path: String, src: Source[ByteString, _], public: Boolean = false): Future[URI] = {
    val mediaType: MediaType = MediaTypes.forExtension(path.substring(path.lastIndexOf(".") + 1))
    // FIXME: If the file is binary this is okay, but otherwise it'll only upload with UTF-8 encoding...
    val contentType = ContentType(mediaType, () => HttpCharsets.`UTF-8`)
    logger.debug(s"Uploading file: $path to $classifier with content-type: $contentType")
    val acl = if (public) CannedAcl.PublicRead else CannedAcl.AuthenticatedRead
    val sink = client.multipartUpload(classifier, path, contentType = contentType, cannedAcl = acl)
    src.runWith(sink).map(r => new URI(r.location.toString))
  }

  override def putFile(classifier: String, path: String, file: File, public: Boolean = false): Future[URI] =
    putBytes(classifier, path, FileIO.fromPath(file.toPath), public)
}
