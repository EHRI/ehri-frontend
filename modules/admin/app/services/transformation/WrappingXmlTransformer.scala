package services.transformation

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import javax.inject.Inject
import models.DataTransformation
import models.DataTransformation.TransformationType

import scala.concurrent.{ExecutionContext, Future}

case class WrappingXmlTransformer @Inject()(
  xsltTransformer: XsltXmlTransformer,
  xqueryTransformer: XQueryXmlTransformer
)(implicit ec: ExecutionContext, mat: Materializer) extends XmlTransformer {

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

  def transformXml(src: Source[ByteString, _], mappings: Seq[(DataTransformation.TransformationType.Value, String)]): Source[ByteString, _] = {
    mappings.foldLeft[Source[ByteString, _]](src) { case (acc, (t, m)) =>
      transformXml(acc, t, m)
    }
  }

  def transformXml(src: Source[ByteString, _], mapType: TransformationType.Value, map: String): Source[ByteString, _] = {
    val dataF: Future[ByteString] = src
      .runFold(ByteString(""))(_ ++ _)
      .map(_.utf8String)
      .map { data =>
        mapType match {
          case TransformationType.Xslt => xsltTransformer.transform(data, map)
          case TransformationType.XQuery => xqueryTransformer.transform(data, map)
        }
      }
      .map(ByteString.fromString)
    Source.future(dataF)
  }
}
