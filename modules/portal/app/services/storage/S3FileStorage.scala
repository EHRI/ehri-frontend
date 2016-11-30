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

  override def putFile(instance: String, classifier: String, path: String, file: File): Future[URI] = {

    val s3config: AwsConfig = AwsConfig.fromConfig(config, fallback = Map("aws.instance" -> instance))
    val cred = AWSCredentials(s3config.accessKey, s3config.secret)
    val client = new S3Client(cred, s3config.region)
    val sink = client.multipartUpload(classifier, path, cannedAcl = CannedAcl.PublicRead)

    logger.debug(s"Uploading file: ${file.getPath} to $classifier/$path")
    FileIO.fromPath(file.toPath).runWith(sink).map(r => new URI(r.location.toString))
  }
}
