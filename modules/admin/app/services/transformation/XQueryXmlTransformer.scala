package services.transformation

import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import javax.inject.Inject

import scala.concurrent.ExecutionContext

case class XQueryXmlTransformer @Inject()()(implicit ec: ExecutionContext, mat: Materializer)
  extends XmlTransformer {
  // TODO: implement
  override def transform: Flow[ByteString, ByteString, _] = Flow[ByteString]
    .map(identity)
}
