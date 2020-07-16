package services.transformation

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.google.inject.ImplementedBy
import models.DataTransformation

@ImplementedBy(classOf[WrappingXmlTransformer])
trait XmlTransformer {
  def transform(mapType: DataTransformation.TransformationType.Value, map: String): Flow[ByteString, ByteString, _]

  def transform(mappings: Seq[(DataTransformation.TransformationType.Value, String)]): Flow[ByteString, ByteString, _]
}
