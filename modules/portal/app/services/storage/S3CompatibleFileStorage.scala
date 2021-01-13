package services.storage

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.alpakka.s3._
import akka.stream.alpakka.s3.headers.CannedAcl
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import play.api.Logger
import software.amazon.awssdk.auth.credentials.{AwsCredentials, AwsCredentialsProvider, StaticCredentialsProvider}
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.providers.AwsRegionProvider
import software.amazon.awssdk.services.s3.{S3Client, S3Configuration}
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.{GetObjectPresignRequest, PutObjectPresignRequest}

import java.net.URI
import scala.collection.JavaConverters._
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}


object S3CompatibleFileStorage {
  def apply(config: com.typesafe.config.Config)(implicit mat: Materializer): S3CompatibleFileStorage = {
    val credentials = StaticCredentialsProvider.create(new AwsCredentials {
      override def accessKeyId(): String = config.getString("aws.credentials.access-key-id")
      override def secretAccessKey(): String = config.getString("aws.credentials.secret-access-key")
    })
    val region: AwsRegionProvider = new AwsRegionProvider {
      override def getRegion: Region = Region.of(config.getString("aws.region.default-region"))
    }

    val endpoint: Option[String] = if (config.hasPath("endpoint-url"))
      Some(config.getString("endpoint-url"))
    else None

    new S3CompatibleFileStorage(credentials, region, endpoint)(mat)
  }
}

case class S3CompatibleFileStorage(
  credentials: AwsCredentialsProvider,
  region: AwsRegionProvider,
  endpointUrl: Option[String] = None
)(implicit mat: Materializer) extends FileStorage {
  private val logger = Logger(getClass)
  private implicit val ec: ExecutionContext = mat.executionContext
  private implicit val actorSystem: ActorSystem = mat.system

  private val endpoint: S3Settings = endpointUrl
    .fold(ifEmpty = S3Ext(actorSystem).settings)(url =>
      S3Ext(actorSystem).settings.withEndpointUrl(url
        .replaceFirst("^https?://", "https://{bucket}.")))
    .withCredentialsProvider(credentials)
    .withS3RegionProvider(region)
    .withAccessStyle(AccessStyle.virtualHostAccessStyle)
    // FIXME: Switch to ListObjectsV2 when Digital Ocean supports it
    .withListBucketApiVersion(ApiVersion.ListBucketVersion1)

  private val clientBuilder = S3Client.builder()
    .credentialsProvider(credentials)
    .region(region.getRegion)
  private val client = endpointUrl
    .fold(clientBuilder)(url => clientBuilder.endpointOverride(URI.create(url)))
    .build()

  override def uri(classifier: String, path: String, duration: FiniteDuration = 10.minutes, contentType: Option[String] = None, versionId: Option[String] = None): URI = {
    val expire = java.time.Duration.ofNanos(duration.toNanos)
    val preSignerBuilder = S3Presigner.builder()
      .credentialsProvider(credentials)
      .region(region.getRegion)
    val preSigner = endpointUrl.fold(preSignerBuilder)(url => preSignerBuilder.endpointOverride(URI.create(url)))
      .build()

    contentType.map { ct =>
      val r = PutObjectRequest.builder()
        .contentType(ct)
        .bucket(classifier)
        .key(path)
        .build()
      val pr = PutObjectPresignRequest.builder()
        .putObjectRequest(r)
        .signatureDuration(expire)
        .build()
      preSigner.presignPutObject(pr).url().toURI
    }.getOrElse {
      val rb = GetObjectRequest.builder()
        .bucket(classifier)
        .key(path)
      val r = versionId.fold(rb)(id => rb.versionId(id)).build()
      val pr = GetObjectPresignRequest.builder()
        .getObjectRequest(r)
        .signatureDuration(expire)
        .build()
      preSigner.presignGetObject(pr).url().toURI
    }
  }

  override def count(classifier: String, prefix: Option[String]): Future[Int] =
    countFilesWithPrefix(classifier, prefix)

  override def info(bucket: String, path: String, versionId: Option[String] = None): Future[Option[(FileMeta, Map[String, String])]] = Future {

    val omrb = HeadObjectRequest.builder()
      .bucket(bucket)
      .key(path)

    val omr = versionId.fold(omrb)(id => omrb.versionId(id)).build()
    try {
      val meta = client.headObject(omr)
      val fm = FileMeta(
        bucket,
        path,
        meta.lastModified,
        meta.contentLength,
        Option(meta.eTag),
        Option(meta.contentType),
        Option(meta.versionId)
      )
      Some((fm, meta.metadata.asScala.toMap))
    } catch {
      case _: AwsServiceException => None
    }
  }(ec)

  override def get(bucket: String, path: String, versionId: Option[String] = None): Future[Option[(FileMeta, Source[ByteString, _])]] = {
    S3.download(bucket, path, versionId = versionId)
      .withAttributes(S3Attributes.settings(endpoint))
      .runWith(Sink.headOption).map(_.flatten)
      .map {
        case Some((src, meta)) => Some(infoToMeta(bucket, path, meta) -> src)
        case _ => None
      }
  }

  override def putBytes(bucket: String, path: String, src: Source[ByteString, _], contentType: Option[String] = None,
    public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI] = {
    val cType = contentType.map(ContentType.parse) match {
      case Some(Right(ct)) => ct
      case _ =>
        val mediaType: MediaType = MediaTypes.forExtension(path.substring(path.lastIndexOf(".") + 1))
        ContentType(mediaType, () => HttpCharsets.`UTF-8`)
    }
    logger.debug(s"Uploading file: $path to $bucket with content-type: $contentType")
    val acl = if (public) CannedAcl.PublicRead else CannedAcl.AuthenticatedRead

    val uploader = S3.multipartUpload(bucket, path, contentType = cType, cannedAcl = acl, metaHeaders = MetaHeaders(meta))
      .withAttributes(S3Attributes.settings(endpoint))

    // FIXME: Annoyingly the Alpakka API returns a relative Uri which doesn't include schemeL
    src.runWith(uploader)
      .map(r => URI.create(r.location.toString.replaceFirst("^(https?://)?", "https://")))
  }

  override def putFile(classifier: String, path: String, file: java.io.File, contentType: Option[String] = None,
    public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI] =
    putBytes(classifier, path, FileIO.fromPath(file.toPath), contentType, public, meta)

  override def deleteFiles(classifier: String, paths: String*): Future[Seq[String]] = Future {
    deleteKeys(classifier, paths)
  }(ec)

  override def deleteFilesWithPrefix(classifier: String, prefix: String): Future[Seq[String]] = Future {
    @scala.annotation.tailrec
    def deleteBatch(done: Seq[String] = Seq.empty): Seq[String] = {
      val fm = listPrefix(classifier, Some(prefix), done.lastOption, max = 1000)
      val keys = fm.files.map(_.key)
      deleteKeys(classifier, keys)
      if (fm.truncated) deleteBatch(done ++ keys)
      else done ++ keys
    }

    deleteBatch()
  }(ec)

  override def streamFiles(classifier: String, prefix: Option[String]): Source[FileMeta, NotUsed] = {
    S3.listBucket(classifier, prefix)
      .withAttributes(S3Attributes.settings(endpoint))
      .map(f => FileMeta(
        classifier,
        f.key,
        f.lastModified,
        f.size,
        // NB: S3 returns eTags wrapped in quotes, but Alpakka doesn't
        // hence for compatibility we add it here.
        Some(f.eTag).map(f => "\"" + f + "\"")
      ))
  }

  override def listFiles(classifier: String, prefix: Option[String], after: Option[String], max: Int): Future[FileList] = Future {
    listPrefix(classifier, prefix, after, max)
  }(ec)

  override def listVersions(classifier: String, path: String, after: Option[String] = None): Future[FileList] =
    listVersions(classifier, Some(path), None, after, max = 200)

  override def setVersioned(classifier: String, enabled: Boolean): Future[Unit] = Future {
    val status = if (enabled) BucketVersioningStatus.ENABLED
    else BucketVersioningStatus.SUSPENDED
    val vc = VersioningConfiguration.builder().status(status).build()
    val bvc = PutBucketVersioningRequest.builder()
      .bucket(classifier)
      .versioningConfiguration(vc)
      .build()

    client.putBucketVersioning(bvc)
    ()
  }(ec)

  override def isVersioned(classifier: String): Future[Boolean] = Future {
    val bvr = GetBucketVersioningRequest.builder().bucket(classifier).build()
    val bvc = client.getBucketVersioning(bvr)
    bvc.status() == BucketVersioningStatus.ENABLED
  }(ec)

  override def fromUri(uri: URI, classifier: String): Future[Option[(FileMeta, Source[ByteString, _])]] = {
    if (uri.getHost.startsWith(classifier)) get(classifier, uri.getPath)
    else if (uri.getPath.startsWith("/" + classifier)) get(classifier, uri.getPath.substring(classifier.length + 1))
    else Future.successful(Option.empty)
  }

  private def infoToMeta(bucket: String, path: String, meta: ObjectMetadata): FileMeta = FileMeta(
    bucket,
    path,
    java.time.Instant.ofEpochMilli(meta.lastModified.clicks),
    meta.getContentLength,
    meta.eTag,
    meta.contentType,
    meta.versionId
  )

  private def countFilesWithPrefix(classifier: String, prefix: Option[String] = None): Future[Int] = Future {
    @scala.annotation.tailrec
    def countBatch(done: Int = 0, last: Option[String] = None): Int = {
      val fm = listPrefix(classifier, prefix, last, max = 1000)
      val count = fm.files.size
      if (fm.truncated) countBatch(done + count, fm.files.lastOption.map(_.key))
      else done + count
    }

    countBatch()
  }(ec)

  private def listVersions(classifier: String, prefix: Option[String], after: Option[String], afterVersion: Option[String], max: Int): Future[FileList] = Future {
    listPrefixVersions(classifier, prefix, after, afterVersion, max)
  }(ec)

  private def listPrefixVersions(classifier: String, prefix: Option[String], after: Option[String], afterVersion: Option[String], max: Int): FileList = {
    val rb = ListObjectVersionsRequest.builder()
      .bucket(classifier)
      .maxKeys(max)
      .prefix(prefix.getOrElse(""))
    val rb2 = after.fold(rb)(key => rb.keyMarker(key))
    val lvr = afterVersion.fold(rb2)(id => rb2.versionIdMarker(id)).build()

    val r = client.listObjectVersions(lvr)
    FileList(r.versions().asScala.map { f =>
      FileMeta(
        classifier,
        f.key,
        f.lastModified,
        f.size,
        eTag = Some(f.eTag),
        versionId = Some(f.versionId)
      )
    }, r.isTruncated)
  }

  private def deleteKeys(classifier: String, paths: Seq[String]) = {
    val delete = Delete.builder()
      .objects(paths.map(key => ObjectIdentifier.builder().key(key).build()): _*)
      .build()
    val dor = DeleteObjectsRequest.builder()
      .bucket(classifier)
      .delete(delete)
      .build()
    client.deleteObjects(dor).deleted().asScala.map(_.key)
  }

  private def listPrefix(classifier: String, prefix: Option[String], after: Option[String], max: Int) = {
    // FIXME: Update this to ListObjectsV2 when Digital Ocean
    // implement support for the StartAfter parameter.
    // For now the marker param in ListObject (v1) seems
    // to do the same thing.
    val rb = ListObjectsRequest.builder()
      .bucket(classifier)
      .maxKeys(max)
    val rb2 = after.fold(rb)(key => rb.marker(key))
    val lor = prefix.fold(rb2)(p => rb2.prefix(p)).build()

    val resp = client.listObjects(lor)
      FileList(resp.contents().asScala.map { f =>
      FileMeta(
        classifier,
        f.key,
        f.lastModified,
        f.size,
        Some(f.eTag),
      )
    }.toList, resp.isTruncated)
  }
}
