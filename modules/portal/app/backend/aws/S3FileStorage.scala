package backend.aws

import java.io.File
import java.net.URI

import awscala.Credentials
import awscala.s3.{Bucket, PutObjectResult, S3}
import backend.FileStorage

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class S3FileStorage()(implicit app: play.api.Application) extends FileStorage {
  override def putFile(instance: String, bucketName: String, path: String, file: File)(implicit executionContext:
  ExecutionContext):
  Future[URI] = {
    val config: AwsConfig = AwsConfig.fromConfig(fallback = Map("aws.instance" -> instance))
    implicit val s3 = S3(Credentials(config.accessKey, config.secret)).at(awscala.Region(config.region))
    val bucket: Bucket = s3.bucket(bucketName).getOrElse(sys.error(s"Bucket $bucketName not found"))
    val read: PutObjectResult = bucket.putAsPublicRead(path, file)
    Future.successful(new URI(s"https://${read.bucket.name}.s3-${config.region}.amazonaws.com/${read.key}"))
  }
}
