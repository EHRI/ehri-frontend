package services.transformation

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.google.inject.ImplementedBy

@ImplementedBy(classOf[XQueryXmlTransformer])
trait XmlTransformer {
  def transform: Flow[ByteString, ByteString, _]
}
