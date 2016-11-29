package services.storage

import java.io.File
import java.net.URI
import javax.inject.Inject

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.s3.auth.AWSCredentials
import akka.stream.alpakka.s3.scaladsl.{MultipartUploadResult, S3Client}
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

case class S3FileStorage @Inject ()(implicit config: play.api.Configuration,
    actorSystem: ActorSystem, materializer: Materializer) extends FileStorage {

  private val logger = Logger(getClass)

  override def putFile(instance: String, classifier: String, path: String, file: File)(
      implicit executionContext: ExecutionContext): Future[URI] = {

    val s3config: AwsConfig = AwsConfig.fromConfig(config, fallback = Map("aws.instance" -> instance))
    val cred = AWSCredentials(s3config.accessKey, s3config.secret)

    val client = new S3Client(cred, s3config.region)
    println(s"Uploading file: ${file.getPath} to $classifier:$path")

    val sink: Sink[ByteString, Future[MultipartUploadResult]] = client.multipartUpload(classifier, path, chunkingParallelism = 1)
    

    FileIO.fromPath(file.toPath).runWith(sink).map(r => new URI(r.location.toString))
  }
}
