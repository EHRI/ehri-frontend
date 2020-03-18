package services.storage

import java.net.URI

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.alpakka.s3.headers.CannedAcl
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.alpakka.s3.{S3Attributes, S3Ext}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.AwsRegionProvider
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.{DeleteObjectsRequest, GeneratePresignedUrlRequest}
import play.api.Logger

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}


private case class S3CompatibleOperations(endpointUrl: Option[String], creds: AWSCredentialsProvider, region: AwsRegionProvider)(implicit actorSystem: ActorSystem, mat: Materializer) {

  private val logger = Logger(getClass)
  private implicit val ec: ExecutionContext = mat.executionContext

  private val endpoint = endpointUrl.fold(
    ifEmpty = S3Ext(actorSystem)
      .settings
      .withCredentialsProvider(creds)
      .withS3RegionProvider(region)
  )(
    url => S3Ext(actorSystem)
      .settings
      .withCredentialsProvider(creds)
      .withS3RegionProvider(region)
      .withEndpointUrl(url)
  )

  private val client = endpointUrl.fold(
    ifEmpty = AmazonS3ClientBuilder.standard()
      .withCredentials(creds)
      .build()
  )(
    url => AmazonS3ClientBuilder.standard()
      .withCredentials(creds)
      .withEndpointConfiguration(new EndpointConfiguration(
        url,
        region.getRegion
      ))
      .build()
  )

  def uri(classifier: String, path: String, duration: FiniteDuration = 10.minutes, contentType: Option[String] = None): URI = {
    val expTime = new java.util.Date()
    var expTimeMillis = expTime.getTime
    expTimeMillis = expTimeMillis + duration.toMillis
    expTime.setTime(expTimeMillis)

    val method = if (contentType.isDefined) com.amazonaws.HttpMethod.PUT else com.amazonaws.HttpMethod.GET
    val psur = new GeneratePresignedUrlRequest(classifier, path)
      .withExpiration(expTime)
      .withMethod(method)
    contentType.foreach { ct =>
      psur.setContentType(ct);
    }

    client.generatePresignedUrl(psur).toURI
  }

  def putBytes(bucket: String, path: String, src: Source[ByteString, _], public: Boolean = false): Future[URI] = {
    val mediaType: MediaType = MediaTypes.forExtension(path.substring(path.lastIndexOf(".") + 1))
    // FIXME: If the file is binary this is okay, but otherwise it'll only upload with UTF-8 encoding...
    val contentType = ContentType(mediaType, () => HttpCharsets.`UTF-8`)
    logger.debug(s"Uploading file: $path to $bucket with content-type: $contentType")
    val acl = if (public) CannedAcl.PublicRead else CannedAcl.AuthenticatedRead

    val uploader = S3.multipartUpload(bucket, path, contentType = contentType, cannedAcl = acl)
    val sink = endpointUrl.fold(uploader)(_ => uploader.withAttributes(S3Attributes.settings(endpoint)))

    src.runWith(sink).map(r => new URI(r.location.toString))
  }

  def putFile(classifier: String, path: String, file: java.io.File, public: Boolean = false): Future[URI] =
    putBytes(classifier, path, FileIO.fromPath(file.toPath), public)

  def listFiles(classifier: String, prefix: Option[String]): Source[FileMeta, NotUsed] = endpointUrl.fold(
    ifEmpty = S3.listBucket(classifier, prefix).map(f => FileMeta(f.key, f.lastModified, f.size))
  )(
    _ => S3.listBucket(classifier, prefix).map(f => FileMeta(f.key, f.lastModified, f.size))
      .withAttributes(S3Attributes.settings(endpoint))
  )

  def deleteFiles(classifier: String, paths: String*): Future[Seq[Boolean]] = Future {
    import collection.JavaConverters._
    val dor = new DeleteObjectsRequest(classifier).withKeys(paths: _*)
    client.deleteObjects(dor).getDeletedObjects.asScala.map(_.isDeleteMarker)
  }(ec)
}
