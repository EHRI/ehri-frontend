package services.transformation

import com.google.inject.ImplementedBy

@ImplementedBy(classOf[BaseXXQueryXmlTransformer])
trait XQueryXmlTransformer {
  def transform(input: String, mapping: String): String
}
