package services.transformation

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import models.DataTransformation
import models.DataTransformation.TransformationType
import play.api.cache.{NamedCache, SyncCacheApi}
import play.api.{Configuration, Logger}

import javax.inject.Inject
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

case class WrappingXmlTransformer @Inject()(
  xsltTransformer: XsltXmlTransformer,
  xqueryTransformer: XQueryXmlTransformer,
  @NamedCache("transformer-cache") cache: SyncCacheApi,
  config: Configuration
)(implicit ec: ExecutionContext, mat: Materializer) extends XmlTransformer with Timer {

  override protected val logger: Logger = Logger(classOf[WrappingXmlTransformer])
  private implicit val cacheTime: Duration = config.get[Duration]("ehri.admin.dataManager.cacheExpiration")

  override def transform(mapType: DataTransformation.TransformationType.Value, map: String): Flow[ByteString, ByteString, _] =
    transform(Seq(mapType -> map))

  override def transform(mappings: Seq[(DataTransformation.TransformationType.Value, String)]): Flow[ByteString, ByteString, _] = {
    Flow[ByteString]
      .prefixAndTail(0)
      .map { case (_, src) =>
        transformXml(src, mappings)
      }
      .flatMapConcat(identity)
  }

  private def transformXml(src: Source[ByteString, _], mappings: Seq[(DataTransformation.TransformationType.Value, String)]): Source[ByteString, _] = {
    val dataF: Future[ByteString] = src
      .runFold(ByteString(""))(_ ++ _)
      .map(_.utf8String)
      .map { data =>
        transformXml(data, mappings)
      }.map(ByteString.fromString)
    Source.future(dataF)
  }

  private def transformXml(src: String, mappings: Seq[(DataTransformation.TransformationType.Value, String)]): String = {
    val md5 = utils.digest(src, mappings)
    cache.getOrElseUpdate(md5, cacheTime) {
      time(s"Transform $md5 (${mappings.size} mappings)") {
        mappings.foldLeft(src) { case (out, (mapType, map)) =>
          logger.debug(s"Running mapType: $mapType...")
          time(s" - transform $mapType") {
            mapType match {
              case TransformationType.Xslt =>
                xsltTransformer.transform(out, map)
              case TransformationType.XQuery => xqueryTransformer.transform(out, map)
            }
          }
        }
      }
    }
  }
}
