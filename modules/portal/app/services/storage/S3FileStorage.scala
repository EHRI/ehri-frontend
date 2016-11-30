package services.storage

import java.io.File
import java.net.URI
import javax.inject.Inject

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.s3.acl.CannedAcl
import akka.stream.alpakka.s3.auth.AWSCredentials
import akka.stream.alpakka.s3.scaladsl.S3Client
import akka.stream.scaladsl.FileIO
import play.api.Logger

import scala.concurrent.Future

case class S3FileStorage @Inject()(implicit config: play.api.Configuration, actorSystem: ActorSystem, mat: Materializer) extends FileStorage {

  private val logger = Logger(getClass)
  private implicit val ec = mat.executionContext
  private val s3config: AwsConfig = AwsConfig.fromConfig(config)
  private val client = new S3Client(AWSCredentials(s3config.accessKey, s3config.secret), s3config.region)

  override def putFile(classifier: String, path: String, file: File): Future[URI] = {
    logger.debug(s"Uploading file: ${file.getPath} to $classifier/$path")
    val sink = client.multipartUpload(classifier, path, cannedAcl = CannedAcl.PublicRead)
    FileIO.fromPath(file.toPath).runWith(sink).map(r => new URI(r.location.toString))
  }
}
