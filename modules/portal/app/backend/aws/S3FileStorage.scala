package backend.aws

import java.io.File
import java.net.URI
import javax.inject.Inject

import awscala._
import awscala.s3._
import backend.FileStorage

import scala.concurrent.{ExecutionContext, Future}

case class S3FileStorage @Inject ()(implicit config: play.api.Configuration) extends FileStorage {

  override def putFile(instance: String, classifier: String, path: String, file: File)(implicit executionContext: ExecutionContext): Future[URI] = {
    val result: PutObjectResult = bucket(instance, classifier).putAsPublicRead(path, file)
    Future.successful(url(instance, result))
  }

  override def delete(instance: String, classifier: String, path: String)(implicit executionContext: ExecutionContext): Future[Unit] = {
    Future.successful(bucket(instance, classifier).delete(path))
  }

  private def bucket(instance: String, classifier: String): Bucket = {
    val s3config: AwsConfig = AwsConfig.fromConfig(fallback = Map("aws.instance" -> instance))
    val s3 = S3(Credentials(s3config.accessKey, s3config.secret))(awscala.Region(s3config.region))
    s3.bucket(classifier).getOrElse(sys.error(s"Bucket $classifier not found"))
  }

  private def url(instance: String, result: PutObjectResult): URI = {
    val s3config: AwsConfig = AwsConfig.fromConfig(fallback = Map("aws.instance" -> instance))
    new URI(s"https://${result.bucket.name}.s3-${s3config.region}.amazonaws.com/${result.key}")
  }
}
