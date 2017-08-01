package services.storage

import java.io.File
import java.net.URI
import javax.inject.Inject

import awscala._
import awscala.s3._

import scala.concurrent.{ExecutionContext, Future}

case class S3FileStorage @Inject ()(config: play.api.Configuration) extends FileStorage {
  override def putFile(instance: String, classifier: String, path: String, file: File)(
      implicit executionContext: ExecutionContext): Future[URI] = {
    val s3config: AwsConfig = AwsConfig.fromConfig(config, fallback = Map("aws.instance" -> instance))
    implicit val s3 = S3(Credentials(s3config.accessKey, s3config.secret))(awscala.Region(s3config.region))
    val bucket: Bucket = s3.bucket(classifier).getOrElse(sys.error(s"Bucket $classifier not found"))
    val read: PutObjectResult = bucket.putAsPublicRead(path, file)
    Future.successful(new URI(s"https://${read.bucket.name}.s3-${s3config.region}.amazonaws.com/${read.key}"))
  }
}
