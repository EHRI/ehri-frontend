package services.transformation

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import eu.ehri.project.xml.{Timer, XQueryXmlTransformer, XsltXmlTransformer}
import models.TransformationType
import play.api.cache.{NamedCache, SyncCacheApi}
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.{Configuration, Logger}
import services.transformation.utils.digest

import javax.inject.Inject
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

case class WrappingXmlTransformer @Inject()(
  xsltTransformer: XsltXmlTransformer,
  xqueryTransformer: XQueryXmlTransformer,
  @NamedCache("transformer-cache") cache: SyncCacheApi,
  config: Configuration
)(implicit ec: ExecutionContext, mat: Materializer) extends XmlTransformer with Timer {

  protected val logger: Logger = Logger(classOf[WrappingXmlTransformer])
  override def logTime(s: String): Unit = logger.debug(s)

  private implicit val cacheTime: Duration = config.get[Duration]("ehri.admin.dataManager.cacheExpiration")

  override def transform(mapType: TransformationType.Value, map: String, params: JsObject = Json.obj()): Flow[ByteString, ByteString, _] =
    transform(Seq((mapType, map, params)))

  override def transform(mappings: Seq[(TransformationType.Value, String, JsObject)]): Flow[ByteString, ByteString, _] = {
    Flow[ByteString]
      .prefixAndTail(0)
      .map { case (_, src) =>
        transformXml(src, mappings)
      }
      .flatMapConcat(identity)
  }

  private def transformXml(src: Source[ByteString, _], mappings: Seq[(TransformationType.Value, String, JsObject)]): Source[ByteString, _] = {
    val dataF: Future[ByteString] = src
      .runFold(ByteString.empty)(_ ++ _)
      .map(_.utf8String)
      .map { data =>
        transformXml(data, mappings)
      }.map(ByteString.fromString)
    Source.future(dataF)
  }

  private def transformXml(src: String, mappings: Seq[(TransformationType.Value, String, JsObject)]): String = {
    val md5 = digest(src, mappings)
    cache.getOrElseUpdate(md5, cacheTime) {
      time(s"Transform $md5 (${mappings.size} mappings)") {
        mappings.foldLeft(src) { case (out, (mapType, map, params)) =>
          logger.debug(s"Running mapType: $mapType...")
          time(s" - transform $mapType") {
            mapType match {
              case TransformationType.Xslt =>
                xsltTransformer.transform(out, map, params)
              case TransformationType.XQuery =>
                val mapParams = params.value.collect { case (key, JsString(value)) => key -> value}
                xqueryTransformer.transform(out, map, mapParams.toMap)
            }
          }
        }
      }
    }
  }
}
