package services.storage

import java.net.URI
import java.util.{Calendar, Date}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.alpakka.s3.headers.CannedAcl
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import javax.inject.Inject
import play.api.Logger

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}


case class S3FileStorage @Inject()(config: play.api.Configuration)(implicit actorSystem: ActorSystem, mat: Materializer) extends FileStorage {

  private val logger = Logger(getClass)
  private implicit val ec: ExecutionContext = mat.executionContext
  private val client = AmazonS3ClientBuilder.standard().build()

  def uri(classifier: String, path: String, duration: FiniteDuration = 10.minutes): URI = {
    val expTime = new java.util.Date()
    var expTimeMillis = expTime.getTime
    expTimeMillis = expTimeMillis + 1000 * 60 * 60
    expTime.setTime(expTimeMillis)

    client
      .generatePresignedUrl(classifier, path, expTime, com.amazonaws.HttpMethod.GET)
      .toURI
  }

  override def putBytes(bucket: String, path: String, src: Source[ByteString, _], public: Boolean = false): Future[URI] = {
    val mediaType: MediaType = MediaTypes.forExtension(path.substring(path.lastIndexOf(".") + 1))
    // FIXME: If the file is binary this is okay, but otherwise it'll only upload with UTF-8 encoding...
    val contentType = ContentType(mediaType, () => HttpCharsets.`UTF-8`)
    logger.debug(s"Uploading file: $path to $bucket with content-type: $contentType")
    val acl = if (public) CannedAcl.PublicRead else CannedAcl.AuthenticatedRead
    val sink = S3.multipartUpload(bucket, path, contentType = contentType, cannedAcl = acl)
    src.runWith(sink).map(r => new URI(r.location.toString))
  }

  override def putFile(classifier: String, path: String, file: java.io.File, public: Boolean = false): Future[URI] =
    putBytes(classifier, path, FileIO.fromPath(file.toPath), public)

  override def listFiles(classifier: String, prefix: Option[String]): Source[FileStorage#File, NotUsed] =
    S3.listBucket(classifier, prefix).map(f => File(f.key, f.lastModified, f.size))
}
