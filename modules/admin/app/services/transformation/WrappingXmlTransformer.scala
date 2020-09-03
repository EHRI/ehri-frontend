package services.transformation

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import javax.inject.Inject
import models.DataTransformation
import models.DataTransformation.TransformationType
import play.api.Logger
import play.api.cache.{NamedCache, SyncCacheApi}

import scala.concurrent.{ExecutionContext, Future}

case class WrappingXmlTransformer @Inject()(
  xsltTransformer: XsltXmlTransformer,
  xqueryTransformer: XQueryXmlTransformer,
  @NamedCache("transformer-cache") cache: SyncCacheApi
)(implicit ec: ExecutionContext, mat: Materializer) extends XmlTransformer with Timer {

  override protected val logger: Logger = Logger(classOf[WrappingXmlTransformer])

  override def transform(mapType: DataTransformation.TransformationType.Value, map: String): Flow[ByteString, ByteString, _] =
    transform(Seq(mapType -> map))

  override def transform(mappings: Seq[(DataTransformation.TransformationType.Value, String)]): Flow[ByteString, ByteString, _] = {
    Flow[ByteString]
      .prefixAndTail(0)
      .map(ht => {
        transformXml(ht._2, mappings)
      })
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
    cache.getOrElseUpdate(md5) {
      time(s"Transform $md5 (${mappings.size} mappings)") {
        mappings.foldLeft(src) { case (out, (mapType, map)) =>
          mapType match {
            case TransformationType.Xslt => xsltTransformer.transform(out, map)
            case TransformationType.XQuery => xqueryTransformer.transform(out, map)
          }
        }
      }
    }
  }
}
